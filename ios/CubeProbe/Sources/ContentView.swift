import SwiftUI
import UIKit

struct ContentView: View {
    @EnvironmentObject private var model: ServerViewModel
    @FocusState private var addressFocused: Bool
    @State private var showClearDialog = false

    var body: some View {
        ZStack {
            background
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        header
                        queryCard
                        if model.isLoading {
                            loadingRow
                        }
                        if let error = model.errorMessage {
                            errorCard(error)
                        }
                        if let status = model.result {
                            StatusCard(status: status, model: model)
                                .id("result")
                                .transition(.opacity.combined(with: .move(edge: .bottom)))
                        }
                        historyCard
                        footer
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 12)
                    .padding(.bottom, 30)
                }
                .scrollDismissesKeyboard(.interactively)
                .scrollIndicators(.hidden)
                .onChange(of: model.result?.id) { newValue in
                    guard newValue != nil else { return }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            proxy.scrollTo("result", anchor: .top)
                        }
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
        .confirmationDialog("清空最近查询？", isPresented: $showClearDialog,
                            titleVisibility: .visible) {
            Button("清空记录", role: .destructive) { model.clearHistory() }
            Button("取消", role: .cancel) {}
        } message: {
            Text("只会删除本机的查询记录，不会影响服务器。")
        }
    }

    private var background: some View {
        ZStack {
            LinearGradient(colors: [Color(hex: "#07131F"), Color(hex: "#040B14")],
                           startPoint: .top, endPoint: .bottom)
            Circle()
                .fill(Color(hex: "#1C6A4A").opacity(0.28))
                .frame(width: 360, height: 360)
                .blur(radius: 100)
                .offset(x: 150, y: -330)
            Circle()
                .fill(Color(hex: "#0D4655").opacity(0.22))
                .frame(width: 300, height: 300)
                .blur(radius: 100)
                .offset(x: -170, y: 180)
        }
        .ignoresSafeArea()
    }

    private var header: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(LinearGradient(colors: [Color(hex: "#8CFFB8"), Color(hex: "#37DC86")],
                                         startPoint: .topLeading, endPoint: .bottomTrailing))
                Image(systemName: "cube.fill")
                    .font(.system(size: 25, weight: .bold))
                    .foregroundColor(Color(hex: "#0A2A18"))
            }
            .frame(width: 46, height: 46)

            VStack(alignment: .leading, spacing: 3) {
                Text("方块探针")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(Color.cpText)
                Text("MINECRAFT SERVER CHECKER")
                    .font(.system(size: 9, weight: .semibold))
                    .tracking(1.4)
                    .foregroundColor(Color.cpMuted)
            }
            Spacer(minLength: 6)
            HStack(spacing: 5) {
                Circle().fill(Color.cpAccent).frame(width: 5, height: 5)
                Text("直连查询")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Color.cpMuted)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .overlay(
                Capsule().stroke(Color.cpLine, lineWidth: 1)
            )
        }
    }

    private var queryCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("SERVER PULSE")
                .font(.system(size: 10, weight: .bold))
                .tracking(1.6)
                .foregroundColor(Color.cpAccent)
            Text("听见服务器的脉搏")
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(Color.cpText)
            Text("直连 Java 与基岩版服务器\n读取 MOTD、在线人数与响应速度。")
                .font(.system(size: 13))
                .foregroundColor(Color.cpMuted)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: 8) {
                modeButton(.java)
                modeButton(.bedrock)
            }
            .padding(4)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.cpInput)
                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(Color.cpLine, lineWidth: 1))
            )

            Text("服务器地址")
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(Color.cpMuted)
                .padding(.top, 2)

            HStack(spacing: 8) {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(Color.cpMuted)
                    TextField("play.example.com 或 IP:端口", text: $model.address)
                        .font(.system(size: 15))
                        .foregroundColor(Color.cpText)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                        .keyboardType(.URL)
                        .submitLabel(.go)
                        .focused($addressFocused)
                        .onSubmit { submit() }
                }
                .padding(.horizontal, 13)
                .padding(.vertical, 13)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color.cpInput)
                        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(addressFocused ? Color.cpAccent.opacity(0.8) : Color.cpLine,
                                    lineWidth: 1))
                )

                Button(action: submit) {
                    Group {
                        if model.isLoading {
                            ProgressView()
                                .tint(Color.cpAccentText)
                        } else {
                            Text("查询")
                                .font(.system(size: 15, weight: .bold))
                        }
                    }
                    .frame(width: 64, height: 48)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(model.isLoading ? Color(hex: "#2E7D5B") : Color.cpAccent)
                    )
                    .foregroundColor(Color.cpAccentText)
                }
                .buttonStyle(.plain)
                .disabled(model.isLoading)
            }

            HStack {
                Text(model.mode == .java ? "默认 25565 · 支持域名与 SRV" : "默认 19132 · UDP 直连")
                    .font(.system(size: 10))
                    .foregroundColor(Color.cpMuted)
                Spacer()
                Button {
                    let text = UIPasteboard.general.string ?? ""
                    if !text.isEmpty {
                        model.address = text.trimmingCharacters(in: .whitespacesAndNewlines)
                        model.errorMessage = nil
                    }
                } label: {
                    Text("粘贴")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(Color.cpAccent)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Capsule().fill(Color.cpInput)
                            .overlay(Capsule().stroke(Color.cpLine, lineWidth: 1)))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(20)
        .cardBackground()
    }

    private func modeButton(_ edition: Edition) -> some View {
        let selected = model.mode == edition
        return Button {
            model.setMode(edition)
        } label: {
            HStack(spacing: 7) {
                Circle()
                    .fill(selected ? edition.accentColor : Color.cpSoft)
                    .frame(width: 5, height: 5)
                Text(edition.title)
                    .font(.system(size: 13, weight: .semibold))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 11, style: .continuous)
                    .fill(selected ? edition.accentColor.opacity(0.16) : Color.clear)
                    .overlay(RoundedRectangle(cornerRadius: 11, style: .continuous)
                        .stroke(selected ? edition.accentColor.opacity(0.45) : Color.clear,
                                lineWidth: 1))
            )
            .foregroundColor(selected ? edition.accentColor : Color.cpMuted)
        }
        .buttonStyle(.plain)
    }

    private var loadingRow: some View {
        HStack(spacing: 10) {
            ProgressView().tint(Color.cpAccent)
            Text("正在查询 \(model.address) …")
                .font(.system(size: 12))
                .foregroundColor(Color.cpAccent)
            Spacer()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.cpSurface))
    }

    private func errorCard(_ message: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.circle.fill")
                .foregroundColor(Color.cpDanger)
            Text(message)
                .font(.system(size: 12))
                .foregroundColor(Color(hex: "#FFD3D9"))
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color(hex: "#2A1820"))
                .overlay(RoundedRectangle(cornerRadius: 14)
                    .stroke(Color(hex: "#542C38"), lineWidth: 1))
        )
    }

    private var historyCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("最近查询")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(Color.cpText)
                    Text("双击 / 轻触即可再次探测")
                        .font(.system(size: 10))
                        .foregroundColor(Color.cpMuted)
                }
                Spacer()
                if !model.history.isEmpty {
                    Button {
                        showClearDialog = true
                    } label: {
                        Text("清空")
                            .font(.system(size: 11))
                            .foregroundColor(Color.cpMuted)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Capsule().fill(Color.cpInput)
                                .overlay(Capsule().stroke(Color.cpLine, lineWidth: 1)))
                    }
                    .buttonStyle(.plain)
                }
            }
            if model.history.isEmpty {
                Text("还没有留下足迹  ·  查询成功的服务器会出现在这里")
                    .font(.system(size: 11))
                    .foregroundColor(Color.cpMuted)
                    .padding(.vertical, 12)
            } else {
                VStack(spacing: 8) {
                    ForEach(model.history) { entry in
                        Button {
                            model.useHistory(entry)
                        } label: {
                            HStack(spacing: 10) {
                                Text(entry.edition == .java ? "J" : "B")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(entry.edition.accentColor)
                                    .frame(width: 30, height: 26)
                                    .background(
                                        RoundedRectangle(cornerRadius: 8)
                                            .fill(entry.edition.accentColor.opacity(0.13))
                                            .overlay(RoundedRectangle(cornerRadius: 8)
                                                .stroke(entry.edition.accentColor.opacity(0.3),
                                                        lineWidth: 1))
                                    )
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(entry.host)
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundColor(Color.cpText)
                                        .lineLimit(1)
                                    Text("\(entry.edition.shortName)  ·  \(entry.port)  ·  \(entry.latencyMs) ms  ·  \(model.relativeTime(entry.timestamp))")
                                        .font(.system(size: 9.5))
                                        .foregroundColor(Color.cpMuted)
                                        .lineLimit(1)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(Color.cpSoft)
                            }
                            .padding(.horizontal, 11)
                            .padding(.vertical, 10)
                            .background(RoundedRectangle(cornerRadius: 14)
                                .fill(Color.cpInput)
                                .overlay(RoundedRectangle(cornerRadius: 14)
                                    .stroke(Color.cpLine, lineWidth: 1)))
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            Button("删除记录", role: .destructive) {
                                model.removeHistory(entry)
                            }
                        }
                    }
                }
            }
        }
        .padding(18)
        .cardBackground()
    }

    private var footer: some View {
        VStack(spacing: 4) {
            Text("结果由目标服务器实时返回  ·  无需登录")
                .font(.system(size: 10))
                .foregroundColor(Color.cpMuted)
            Text("Java SLP  ·  Bedrock RakNet  ·  v1.0.0")
                .font(.system(size: 9))
                .foregroundColor(Color(hex: "#4C6074"))
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 4)
    }

    private func submit() {
        addressFocused = false
        model.query()
    }
}

// MARK: - Result card

private struct StatusCard: View {
    let status: ServerStatus
    @ObservedObject var model: ServerViewModel

    private var accent: Color { status.edition.accentColor }

    private var details: String {
        var parts: [String] = []
        if status.edition == .java {
            parts.append("Java Edition")
            if status.protocolNumber > 0 { parts.append("协议 \(status.protocolNumber)") }
            let software = model.prettySoftware(status.software)
            if !software.isEmpty { parts.append(software) }
            if status.secureChat { parts.append("安全聊天") }
            if status.legacy { parts.append("Legacy Ping") }
            if status.viaSRV { parts.append("SRV") }
        } else {
            parts.append("Bedrock Edition")
            if status.protocolNumber > 0 { parts.append("协议 \(status.protocolNumber)") }
            if !status.gameMode.isEmpty { parts.append(model.displayGameMode(status.gameMode)) }
            parts.append("UDP")
        }
        return parts.joined(separator: "   ·   ")
    }

    private var percent: Int {
        guard status.maxPlayers > 0 else { return 0 }
        return min(100, Int(round(Double(status.online) * 100 / Double(status.maxPlayers))))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 15) {
            HStack(alignment: .top, spacing: 12) {
                icon
                VStack(alignment: .leading, spacing: 4) {
                    Text(status.host)
                        .font(.system(size: 19, weight: .bold))
                        .foregroundColor(Color.cpText)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                    Text("\(status.edition.shortName) Edition  ·  \(status.host):\(status.port)"
                         + (status.viaSRV ? "  ·  SRV" : ""))
                        .font(.system(size: 10))
                        .foregroundColor(Color.cpMuted)
                        .lineLimit(1)
                }
                Spacer(minLength: 4)
                HStack(spacing: 5) {
                    Circle().fill(accent).frame(width: 6, height: 6)
                    Text("在线")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(accent)
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 6)
                .background(Capsule().fill(accent.opacity(0.12))
                    .overlay(Capsule().stroke(accent.opacity(0.35), lineWidth: 1)))
            }

            motdText(status.motdSegments)
                .font(.system(size: 16))
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)

            Divider().overlay(Color.cpLine)

            HStack(spacing: 8) {
                statCell(title: "在线玩家", value: "\(status.online)",
                         suffix: status.maxPlayers > 0 ? "/ \(status.maxPlayers)" : "", accent: accent)
                statCell(title: "响应", value: "\(status.latencyMs)", suffix: "ms", accent: nil)
                statCell(title: "版本", value: status.versionName, suffix: "", accent: nil,
                         compact: true)
            }

            VStack(spacing: 5) {
                ProgressView(value: Double(percent), total: 100)
                    .tint(accent)
                    .progressViewStyle(.linear)
                    .scaleEffect(x: 1, y: 1.4, anchor: .center)
                HStack {
                    Text("服务器容量")
                        .font(.system(size: 9))
                        .foregroundColor(Color.cpSoft)
                    Spacer()
                    Text(status.maxPlayers > 0
                         ? "\(status.online) / \(status.maxPlayers)  ·  \(percent)%"
                         : "未公开")
                        .font(.system(size: 9))
                        .foregroundColor(Color.cpMuted)
                }
            }

            Text(details)
                .font(.system(size: 10))
                .foregroundColor(Color.cpMuted)
                .fixedSize(horizontal: false, vertical: true)

            if status.edition == .java, !status.sample.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("玩家样本")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(Color.cpMuted)
                        Spacer()
                        Text("\(status.online) 位玩家在线")
                            .font(.system(size: 9))
                            .foregroundColor(Color.cpSoft)
                    }
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 68), spacing: 7)],
                              alignment: .leading, spacing: 7) {
                        ForEach(Array(status.sample.prefix(24).enumerated()), id: \.offset) { item in
                            motdText(MOTDRenderer.legacySegments(item.element))
                                .font(.system(size: 10.5))
                                .lineLimit(1)
                                .padding(.horizontal, 9)
                                .padding(.vertical, 5)
                                .background(Capsule().fill(Color.cpInput)
                                    .overlay(Capsule().stroke(Color.cpLine, lineWidth: 1)))
                        }
                    }
                }
            }

            HStack(spacing: 8) {
                Button {
                    model.query()
                } label: {
                    Text("重新探测")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Color.cpMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .background(RoundedRectangle(cornerRadius: 11)
                            .fill(Color.cpInput)
                            .overlay(RoundedRectangle(cornerRadius: 11)
                                .stroke(Color.cpLine, lineWidth: 1)))
                }
                .buttonStyle(.plain)

                Button {
                    model.copySummary()
                } label: {
                    Text("复制状态")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Color.cpMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .background(RoundedRectangle(cornerRadius: 11)
                            .fill(Color.cpInput)
                            .overlay(RoundedRectangle(cornerRadius: 11)
                                .stroke(Color.cpLine, lineWidth: 1)))
                }
                .buttonStyle(.plain)

                if let summary = model.shareText {
                    ShareLink(item: summary) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(Color.cpMuted)
                            .frame(width: 42, height: 40)
                            .background(RoundedRectangle(cornerRadius: 11)
                                .fill(Color.cpInput)
                                .overlay(RoundedRectangle(cornerRadius: 11)
                                    .stroke(Color.cpLine, lineWidth: 1)))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(LinearGradient(colors: [Color(hex: "#15293B"), Color(hex: "#0B1927")],
                                     startPoint: .topLeading, endPoint: .bottomTrailing))
                .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .stroke(Color.cpLine, lineWidth: 1))
        )
    }

    private var icon: some View {
        Group {
            if let encoded = status.faviconBase64,
               let data = Data(base64Encoded: encoded),
               let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .interpolation(.none)
                    .frame(width: 56, height: 56)
                    .clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 13, style: .continuous)
                        .stroke(accent.opacity(0.35), lineWidth: 1))
            } else {
                ZStack {
                    RoundedRectangle(cornerRadius: 13, style: .continuous)
                        .fill(Color.cpInput)
                        .overlay(RoundedRectangle(cornerRadius: 13, style: .continuous)
                            .stroke(accent.opacity(0.35), lineWidth: 1))
                    Image(systemName: "cube.fill")
                        .font(.system(size: 25, weight: .medium))
                        .foregroundColor(accent)
                }
                .frame(width: 56, height: 56)
            }
        }
    }

    private func statCell(title: String, value: String, suffix: String,
                          accent: Color?, compact: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 9))
                .foregroundColor(Color.cpSoft)
            HStack(alignment: .lastTextBaseline, spacing: 3) {
                Text(value)
                    .font(.system(size: compact ? 12 : 20, weight: .bold))
                    .foregroundColor(accent ?? Color.cpText)
                    .lineLimit(compact ? 2 : 1)
                    .minimumScaleFactor(0.7)
                if !suffix.isEmpty {
                    Text(suffix)
                        .font(.system(size: 9))
                        .foregroundColor(Color.cpSoft)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 13)
                .fill(Color(hex: "#0A1725"))
                .overlay(RoundedRectangle(cornerRadius: 13)
                    .stroke(Color(hex: "#1D3346"), lineWidth: 1))
        )
    }
}

private func motdText(_ segments: [MOTDSegment]) -> Text {
    segments.reduce(Text("")) { partial, segment in
        var piece = Text(segment.text)
        if let hex = segment.colorHex {
            piece = piece.foregroundColor(Color(hex: hex))
        }
        if segment.bold {
            piece = piece.fontWeight(.semibold)
        }
        if segment.italic {
            piece = piece.italic()
        }
        if segment.underline {
            piece = piece.underline()
        }
        if segment.strike {
            piece = piece.strikethrough()
        }
        return partial + piece
    }
}

private extension View {
    func cardBackground() -> some View {
        self.background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Color.cpSurface)
                .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .stroke(Color.cpLine, lineWidth: 1))
        )
    }
}

extension Color {
    static let cpBG = Color(hex: "#06101C")
    static let cpSurface = Color(hex: "#0E1D2D")
    static let cpSurfaceHigh = Color(hex: "#152A3D")
    static let cpInput = Color(hex: "#081724")
    static let cpLine = Color(hex: "#233B4E")
    static let cpText = Color(hex: "#F2F8F5")
    static let cpMuted = Color(hex: "#8FA3B8")
    static let cpSoft = Color(hex: "#5F7488")
    static let cpAccent = Color(hex: "#62F2A2")
    static let cpAccentText = Color(hex: "#042114")
    static let cpDanger = Color(hex: "#FF6D7D")
}
