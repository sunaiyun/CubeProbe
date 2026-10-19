import Foundation
import SwiftUI
import Combine
import UIKit

@MainActor
final class ServerViewModel: ObservableObject {
    @Published var mode: Edition = .java
    @Published var address: String = ""
    @Published var isLoading = false
    @Published var result: ServerStatus?
    @Published var errorMessage: String?
    @Published var history: [HistoryEntry] = []

    private let client = MinecraftClient.shared
    private let historyKey = "cubeprobe.history.v1"
    private let stateKey = "cubeprobe.state.v1"
    private var task: Task<Void, Never>?

    init() {
        load()
    }

    func setMode(_ newMode: Edition) {
        guard !isLoading, newMode != mode else { return }
        mode = newMode
        result = nil
        errorMessage = nil
        saveState()
    }

    func query() {
        guard !isLoading else { return }
        let trimmed = address.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            errorMessage = "先告诉我服务器地址吧。"
            return
        }
        let parsed: ServerAddress
        do {
            parsed = try ServerAddress.parse(trimmed, edition: mode)
        } catch {
            result = nil
            errorMessage = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            return
        }
        saveState()
        errorMessage = nil
        result = nil
        isLoading = true
        let requestedMode = mode
        task?.cancel()
        task = Task {
            do {
                let status = try await client.query(address: parsed, edition: requestedMode)
                guard !Task.isCancelled else { return }
                result = status
                addHistory(address: parsed, status: status)
            } catch {
                guard !Task.isCancelled else { return }
                if requestedMode == .bedrock, let queryError = error as? QueryError,
                   case .timeout = queryError {
                    errorMessage = "没有收到基岩版响应。请确认端口（默认 19132）以及服务器是否在线。"
                } else {
                    errorMessage = (error as? LocalizedError)?.errorDescription
                        ?? error.localizedDescription
                }
            }
            isLoading = false
        }
    }

    func useHistory(_ entry: HistoryEntry) {
        guard !isLoading else { return }
        mode = entry.edition
        address = entry.inputValue
        result = nil
        errorMessage = nil
        saveState()
        query()
    }

    func removeHistory(_ entry: HistoryEntry) {
        history.removeAll { $0.id == entry.id }
        saveHistory()
    }

    func clearHistory() {
        history = []
        saveHistory()
    }

    func copySummary() {
        guard let summary = shareText else { return }
        UIPasteboard.general.string = summary
    }

    var shareText: String? {
        guard let status = result else { return nil }
        let editionName = status.edition == .bedrock ? "Bedrock Edition" : "Java Edition"
        var lines: [String] = [
            "方块探针 · 服务器状态",
            "\(editionName)  ·  \(status.host):\(status.port)",
            "",
            status.motdPlain,
            "",
            "在线玩家：\(status.online)" + (status.maxPlayers > 0 ? " / \(status.maxPlayers)" : ""),
            "响应：\(status.latencyMs) ms",
            "版本：\(status.versionName)",
        ]
        if status.protocolNumber > 0 {
            lines.append("协议：\(status.protocolNumber)")
        }
        if status.edition == .bedrock, !status.gameMode.isEmpty {
            lines.append("游戏模式：\(displayGameMode(status.gameMode))")
        }
        return lines.joined(separator: "\n")
    }

    func displayGameMode(_ value: String) -> String {
        switch value.trimmingCharacters(in: .whitespaces).lowercased() {
        case "survival": return "生存"
        case "creative": return "创造"
        case "adventure": return "冒险"
        case "spectator": return "旁观"
        default: return value
        }
    }

    func prettySoftware(_ value: String) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespaces)
        if trimmed.isEmpty { return "" }
        let lower = trimmed.lowercased()
        if lower == "fml" || lower == "forge" { return "Forge" }
        if lower.contains("fabric") { return "Fabric" }
        if lower.contains("quilt") { return "Quilt" }
        if lower == "modded" { return "Modded" }
        return trimmed
    }

    func relativeTime(_ date: Date) -> String {
        let delta = Date().timeIntervalSince(date)
        if delta < 60 { return "刚刚" }
        if delta < 3600 { return "\(Int(delta / 60)) 分钟前" }
        if delta < 86400 { return "\(Int(delta / 3600)) 小时前" }
        if delta < 86400 * 7 { return "\(Int(delta / 86400)) 天前" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "M月d日"
        return formatter.string(from: date)
    }

    // MARK: - Persistence

    private struct State: Codable {
        var mode: Edition
        var address: String
    }

    private func addHistory(address: ServerAddress, status: ServerStatus) {
        history.removeAll {
            $0.edition == status.edition
                && $0.host.caseInsensitiveCompare(address.host) == .orderedSame
                && $0.port == address.port
        }
        history.insert(HistoryEntry(edition: status.edition, host: address.host,
                                    port: address.port,
                                    explicitPort: address.explicitPort,
                                    latencyMs: status.latencyMs,
                                    timestamp: Date()), at: 0)
        history = Array(history.prefix(8))
        saveHistory()
    }

    private func saveHistory() {
        if let data = try? JSONEncoder().encode(history) {
            UserDefaults.standard.set(data, forKey: historyKey)
        }
    }

    private func saveState() {
        let state = State(mode: mode, address: address.trimmingCharacters(in: .whitespacesAndNewlines))
        if let data = try? JSONEncoder().encode(state) {
            UserDefaults.standard.set(data, forKey: stateKey)
        }
    }

    private func load() {
        if let data = UserDefaults.standard.data(forKey: historyKey),
           let decoded = try? JSONDecoder().decode([HistoryEntry].self, from: data) {
            history = Array(decoded.prefix(8))
        }
        if let data = UserDefaults.standard.data(forKey: stateKey),
           let decoded = try? JSONDecoder().decode(State.self, from: data) {
            mode = decoded.mode
            address = decoded.address
        }
        if address.isEmpty {
            address = ""
        }
    }
}
