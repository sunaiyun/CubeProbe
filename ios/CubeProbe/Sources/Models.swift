import Foundation
import SwiftUI

enum Edition: String, Codable, CaseIterable, Identifiable {
    case java
    case bedrock

    var id: String { rawValue }

    var title: String {
        switch self {
        case .java: return "Java 版"
        case .bedrock: return "基岩版"
        }
    }

    var shortName: String {
        switch self {
        case .java: return "Java"
        case .bedrock: return "Bedrock"
        }
    }

    var defaultPort: UInt16 {
        switch self {
        case .java: return 25565
        case .bedrock: return 19132
        }
    }

    var accentHex: String {
        switch self {
        case .java: return "#62F2A2"
        case .bedrock: return "#4EC8FF"
        }
    }

    var accentColor: Color { Color(hex: accentHex) }
}

struct ServerAddress: Equatable, Hashable {
    let host: String
    let port: UInt16
    let explicitPort: Bool

    var display: String {
        let rendered = host.contains(":") ? "[\(host)]" : host
        return "\(rendered):\(port)"
    }

    var inputValue: String {
        guard explicitPort else { return host }
        let rendered = host.contains(":") ? "[\(host)]" : host
        return "\(rendered):\(port)"
    }

    var isIPLiteral: Bool {
        if host.contains(":") { return true }
        return !host.isEmpty && host.allSatisfy { $0.isNumber || $0 == "." }
    }

    static func parse(_ raw: String, edition: Edition) throws -> ServerAddress {
        var text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { throw QueryError.invalid("empty address") }
        let lower = text.lowercased()
        for scheme in ["minecraft://", "mc://", "https://", "http://"] {
            if lower.hasPrefix(scheme) {
                text = String(text.dropFirst(scheme.count))
                break
            }
        }
        if let slash = text.firstIndex(of: "/") {
            text = String(text[..<slash])
        }
        text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, text.count <= 260 else {
            throw QueryError.invalid("bad address")
        }

        var host: String
        var port = edition.defaultPort
        var explicit = false

        if text.hasPrefix("[") {
            guard let closing = text.firstIndex(of: "]") else {
                throw QueryError.invalid("bad IPv6")
            }
            host = String(text[text.index(after: text.startIndex)..<closing])
            let rest = String(text[text.index(after: closing)...])
            if rest.hasPrefix(":") {
                port = try parsePort(String(rest.dropFirst()))
                explicit = true
            } else if !rest.isEmpty {
                throw QueryError.invalid("bad IPv6 suffix")
            }
        } else {
            let colonCount = text.filter { $0 == ":" }.count
            if colonCount == 1 {
                let parts = text.split(separator: ":", omittingEmptySubsequences: false)
                guard parts.count == 2 else { throw QueryError.invalid("bad address") }
                let tail = String(parts[1])
                guard !tail.isEmpty, tail.allSatisfy({ $0.isNumber }) else {
                    throw QueryError.invalid("bad port")
                }
                host = String(parts[0])
                port = try parsePort(tail)
                explicit = true
            } else if colonCount > 1 {
                host = text
            } else {
                host = text
            }
        }

        if host.hasSuffix(".") { host = String(host.dropLast()) }
        guard !host.isEmpty, host.count <= 253 else {
            throw QueryError.invalid("bad host")
        }
        for character in host {
            if character.isWhitespace || "/\\@?#".contains(character) {
                throw QueryError.invalid("bad host character")
            }
        }
        return ServerAddress(host: host, port: port, explicitPort: explicit)
    }

    private static func parsePort(_ value: String) throws -> UInt16 {
        guard !value.isEmpty, value.count <= 5,
              let number = Int(value), (1...65535).contains(number) else {
            throw QueryError.invalid("port out of range")
        }
        return UInt16(number)
    }
}

enum QueryError: LocalizedError {
    case invalid(String)
    case unknownHost
    case timeout
    case refused
    case network
    case protocolError(String)

    var errorDescription: String? {
        switch self {
        case .invalid:
            return "服务器地址格式不正确。"
        case .unknownHost:
            return "找不到这个服务器，请检查域名或网络连接。"
        case .timeout:
            return "连接超时。请确认端口开放、服务器在线，并检查 Java / 基岩版是否选对。"
        case .refused:
            return "连接被拒绝，端口可能未开放。"
        case .network:
            return "网络不可用，请检查 Wi-Fi 或移动网络。"
        case .protocolError(let message):
            return message.isEmpty ? "服务器返回了无法识别的数据。" : "查询失败：\(message)"
        }
    }
}

struct MOTDSegment: Hashable {
    var text: String
    var colorHex: String?
    var bold: Bool = false
    var italic: Bool = false
    var underline: Bool = false
    var strike: Bool = false
}

struct ServerStatus: Identifiable {
    let id = UUID()
    var edition: Edition
    var host: String
    var port: UInt16
    var versionName: String
    var protocolNumber: Int
    var online: Int
    var maxPlayers: Int
    var latencyMs: Int
    var motdSegments: [MOTDSegment]
    var motdPlain: String
    var faviconBase64: String?
    var sample: [String]
    var software: String
    var secureChat: Bool
    var gameMode: String
    var subMotd: String
    var legacy: Bool
    var viaSRV: Bool
    var target: String?

    init(edition: Edition, host: String, port: UInt16, versionName: String,
         protocolNumber: Int, online: Int, maxPlayers: Int, latencyMs: Int,
         motdSegments: [MOTDSegment], motdPlain: String,
         faviconBase64: String? = nil, sample: [String] = [],
         software: String = "", secureChat: Bool = false,
         gameMode: String = "", subMotd: String = "", legacy: Bool = false,
         viaSRV: Bool = false, target: String? = nil) {
        self.edition = edition
        self.host = host
        self.port = port
        self.versionName = versionName
        self.protocolNumber = protocolNumber
        self.online = online
        self.maxPlayers = maxPlayers
        self.latencyMs = latencyMs
        self.motdSegments = motdSegments
        self.motdPlain = motdPlain
        self.faviconBase64 = faviconBase64
        self.sample = sample
        self.software = software
        self.secureChat = secureChat
        self.gameMode = gameMode
        self.subMotd = subMotd
        self.legacy = legacy
        self.viaSRV = viaSRV
        self.target = target
    }
}

struct HistoryEntry: Codable, Identifiable, Equatable {
    var id = UUID()
    var edition: Edition
    var host: String
    var port: UInt16
    var explicitPort: Bool
    var latencyMs: Int
    var timestamp: Date

    init(id: UUID = UUID(), edition: Edition, host: String, port: UInt16,
         explicitPort: Bool, latencyMs: Int, timestamp: Date) {
        self.id = id
        self.edition = edition
        self.host = host
        self.port = port
        self.explicitPort = explicitPort
        self.latencyMs = latencyMs
        self.timestamp = timestamp
    }

    var inputValue: String {
        guard explicitPort else { return host }
        let rendered = host.contains(":") ? "[\(host)]" : host
        return "\(rendered):\(port)"
    }
}

extension Color {
    init(hex: String) {
        let cleaned = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var value: UInt64 = 0
        Scanner(string: cleaned).scanHexInt64(&value)
        let red, green, blue: Double
        if cleaned.count == 6 {
            red = Double((value >> 16) & 0xFF) / 255
            green = Double((value >> 8) & 0xFF) / 255
            blue = Double(value & 0xFF) / 255
        } else {
            red = 1; green = 1; blue = 1
        }
        self.init(red: red, green: green, blue: blue)
    }
}
