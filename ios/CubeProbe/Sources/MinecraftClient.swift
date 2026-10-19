import Foundation
import Network
import Darwin

final class MinecraftClient {

    static let shared = MinecraftClient()

    private static let networkQueue = DispatchQueue(label: "com.cubeprobe.network")
    private static let raknetMagic = Data([
        0x00, 0xFF, 0xFF, 0x00, 0xFE, 0xFE, 0xFE, 0xFE,
        0xFD, 0xFD, 0xFD, 0xFD, 0x12, 0x34, 0x56, 0x78,
    ])

    private static let session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 2.0
        configuration.timeoutIntervalForResource = 4.0
        configuration.waitsForConnectivity = false
        return URLSession(configuration: configuration)
    }()

    func query(address: ServerAddress, edition: Edition) async throws -> ServerStatus {
        switch edition {
        case .java:
            return try await queryJava(address)
        case .bedrock:
            return try await queryBedrock(address)
        }
    }

    // MARK: - Java Edition

    private func queryJava(_ address: ServerAddress) async throws -> ServerStatus {
        var targetHost = address.host
        var targetPort = address.port
        var viaSRV = false
        if !address.explicitPort, !address.isIPLiteral {
            var record: SRVRecord?
            do {
                record = try await withTimeout(4.5) {
                    await self.resolveSRV(host: address.host)
                }
            } catch {
                record = nil
            }
            if let record {
                targetHost = record.host
                targetPort = record.port
                viaSRV = true
            }
        }

        do {
            return try await withTimeout(9) {
                try await self.queryJavaModern(address: address, targetHost: targetHost,
                                               targetPort: targetPort, viaSRV: viaSRV)
            }
        } catch let error as QueryError {
            switch error {
            case .unknownHost, .network:
                throw error
            default:
                return try await withTimeout(7) {
                    try await self.queryJavaLegacy(address: address, targetHost: targetHost,
                                                   targetPort: targetPort, viaSRV: viaSRV)
                }
            }
        }
    }

    private func queryJavaModern(address: ServerAddress, targetHost: String,
                                 targetPort: UInt16, viaSRV: Bool) async throws -> ServerStatus {
        let started = Date()
        let connection = TCPConnection(host: targetHost, port: targetPort)
        do {
            try await connection.start()
            var hostBytes = Data(address.host.utf8)
            var handshake = Data()
            appendVarInt(0, to: &handshake)
            appendVarInt(-1, to: &handshake)
            appendVarInt(Int32(hostBytes.count), to: &handshake)
            handshake.append(hostBytes)
            appendUInt16(targetPort, to: &handshake)
            appendVarInt(1, to: &handshake)

            var framed = Data()
            appendVarInt(Int32(handshake.count), to: &framed)
            framed.append(handshake)
            framed.append(contentsOf: [UInt8(0x01), UInt8(0x00)]) // status request packet
            try await connection.send(framed)

            let length = Int(try await connection.readVarInt())
            guard length > 0, length <= 2 * 1024 * 1024 else {
                throw QueryError.protocolError("响应长度异常")
            }
            let payload = try await connection.readExactly(length)
            var cursor = 0
            let packetID = try readVarInt(payload, cursor: &cursor)
            guard packetID == 0 else {
                throw QueryError.protocolError("响应包类型异常")
            }
            let stringLength = Int(try readVarInt(payload, cursor: &cursor))
            guard stringLength >= 0, cursor + stringLength <= payload.count else {
                throw QueryError.protocolError("响应字符串截断")
            }
            let jsonData = payload.subdata(in: cursor..<(cursor + stringLength))
            let status = try parseJavaStatus(address: address, targetHost: targetHost,
                                             targetPort: targetPort, viaSRV: viaSRV,
                                             jsonData: jsonData,
                                             latency: millisecondsSince(started))
            connection.close()
            return status
        } catch {
            connection.close()
            throw mapConnectionError(error)
        }
    }

    private func queryJavaLegacy(address: ServerAddress, targetHost: String,
                                 targetPort: UInt16, viaSRV: Bool) async throws -> ServerStatus {
        let started = Date()
        let connection = TCPConnection(host: targetHost, port: targetPort)
        do {
            try await connection.start()
            try await connection.send(Data([0xFE, 0x01]))
            let header = try await connection.readExactly(1)
            guard header.first == 0xFF else {
                throw QueryError.protocolError("不是旧版状态响应")
            }
            let lengthData = try await connection.readExactly(2)
            let characters = (Int(lengthData[0]) << 8) | Int(lengthData[1])
            guard characters > 0, characters <= 32767 else {
                throw QueryError.protocolError("旧版响应长度异常")
            }
            let encoded = try await connection.readExactly(characters * 2)
            guard let text = String(data: encoded, encoding: .utf16BigEndian) else {
                throw QueryError.protocolError("旧版响应编码异常")
            }
            let fields = text.components(separatedBy: "\u{0000}")
            let motd = fields.count > 3 ? fields[3] : text
            let version = fields.count > 2 ? fields[2] : "旧版服务器"
            let protocolNumber = fields.count > 1 ? (Int(fields[1]) ?? -1) : -1
            let online = fields.count > 4 ? (Int(fields[4]) ?? 0) : 0
            let maximum = fields.count > 5 ? (Int(fields[5]) ?? 0) : 0
            connection.close()
            return makeStatus(edition: .java, address: address, targetHost: targetHost,
                              targetPort: targetPort, viaSRV: viaSRV,
                              version: version, protocolNumber: protocolNumber,
                              online: max(0, online), maximum: max(0, maximum),
                              description: motd, latency: millisecondsSince(started),
                              legacy: true)
        } catch {
            connection.close()
            throw mapConnectionError(error)
        }
    }

    private func parseJavaStatus(address: ServerAddress, targetHost: String,
                                 targetPort: UInt16, viaSRV: Bool, jsonData: Data,
                                 latency: Int) throws -> ServerStatus {
        guard let root = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any] else {
            throw QueryError.protocolError("无法解析服务器 JSON")
        }
        let versionObject = root["version"] as? [String: Any]
        let playersObject = root["players"] as? [String: Any]
        let version = versionObject?["name"] as? String ?? "未知版本"
        let protocolNumber = versionObject?["protocol"] as? Int ?? -1
        let online = max(0, playersObject?["online"] as? Int ?? 0)
        let maximum = max(0, playersObject?["max"] as? Int ?? 0)
        var sample: [String] = []
        if let sampleArray = playersObject?["sample"] as? [Any] {
            sample = sampleArray.prefix(80).compactMap { item in
                if let object = item as? [String: Any] {
                    return object["name"] as? String
                }
                return item as? String
            }.filter { !$0.isEmpty }
        }
        var software = ""
        if let modInfo = root["modinfo"] as? [String: Any]
            ?? root["modInfo"] as? [String: Any] {
            software = modInfo["type"] as? String ?? "Modded"
        }
        if root["forgeData"] != nil {
            software = "Forge"
        }
        var favicon = root["favicon"] as? String ?? ""
        if favicon.hasPrefix("data:image/png;base64,") {
            favicon = String(favicon.dropFirst("data:image/png;base64,".count))
        }
        if favicon.count > 350_000 { favicon = "" }
        let secure = root["enforcesSecureChat"] as? Bool ?? false
        let description = root["description"] ?? "A Minecraft Server"

        var status = makeStatus(edition: .java, address: address, targetHost: targetHost,
                                targetPort: targetPort, viaSRV: viaSRV,
                                version: version, protocolNumber: protocolNumber,
                                online: online, maximum: maximum,
                                description: description, latency: latency,
                                legacy: false)
        status.faviconBase64 = favicon.isEmpty ? nil : favicon
        status.sample = sample
        status.software = software
        status.secureChat = secure
        return status
    }

    // MARK: - Bedrock Edition

    private func queryBedrock(_ address: ServerAddress) async throws -> ServerStatus {
        try await withTimeout(10) {
            try await self.queryBedrockBody(address)
        }
    }

    private func queryBedrockBody(_ address: ServerAddress) async throws -> ServerStatus {
        let connection = UDPConnection(host: address.host, port: address.port)
        do {
            try await connection.start()
            var packet = Data([0x01])
            appendUInt64(UInt64(Date().timeIntervalSince1970 * 1000), to: &packet)
            packet.append(Self.raknetMagic)
            appendUInt64(UInt64.random(in: 0...UInt64.max), to: &packet)

            for _ in 0..<3 {
                try await connection.send(packet)
                try await Task.sleep(nanoseconds: 200_000_000)
            }
            let lastSend = Date()
            let response = try await withTimeout(4.5) {
                try await connection.receive()
            }
            connection.close()
            return try parseBedrockStatus(address: address, data: response,
                                          latency: millisecondsSince(lastSend))
        } catch {
            connection.close()
            throw mapConnectionError(error)
        }
    }

    private func parseBedrockStatus(address: ServerAddress, data: Data,
                                    latency: Int) throws -> ServerStatus {
        guard data.count >= 35, data[0] == 0x1C else {
            throw QueryError.protocolError("不是 RakNet 响应")
        }
        guard data.subdata(in: 17..<33) == Self.raknetMagic else {
            throw QueryError.protocolError("RakNet magic 不匹配")
        }
        let textLength = (Int(data[33]) << 8) | Int(data[34])
        guard textLength > 0, data.count >= 35 + textLength else {
            throw QueryError.protocolError("RakNet 响应截断")
        }
        guard let text = String(data: data.subdata(in: 35..<(35 + textLength)),
                                encoding: .utf8) else {
            throw QueryError.protocolError("RakNet 文本编码异常")
        }
        let fields = text.components(separatedBy: ";")
        guard fields.count >= 6 else {
            throw QueryError.protocolError("RakNet 字段不足")
        }
        let motd = fields.count > 1 ? fields[1] : ""
        let protocolNumber = fields.count > 2 ? (Int(fields[2]) ?? -1) : -1
        let version = fields.count > 3 ? fields[3] : ""
        let online = fields.count > 4 ? (Int(fields[4]) ?? 0) : 0
        let maximum = fields.count > 5 ? (Int(fields[5]) ?? 0) : 0
        let subMotd = fields.count > 7 ? fields[7] : ""
        let gameMode = fields.count > 8 ? fields[8] : ""
        let combined = subMotd.isEmpty ? motd : "\(motd)\n\(subMotd)"
        var status = makeStatus(edition: .bedrock, address: address,
                                targetHost: address.host, targetPort: address.port,
                                viaSRV: false, version: version,
                                protocolNumber: protocolNumber,
                                online: max(0, online), maximum: max(0, maximum),
                                description: combined, latency: latency, legacy: false)
        status.gameMode = gameMode
        status.subMotd = subMotd
        return status
    }

    // MARK: - Status construction

    private func makeStatus(edition: Edition, address: ServerAddress,
                            targetHost: String, targetPort: UInt16, viaSRV: Bool,
                            version: String, protocolNumber: Int, online: Int,
                            maximum: Int, description: Any?, latency: Int,
                            legacy: Bool) -> ServerStatus {
        var segments = MOTDRenderer.segments(from: description)
        var plain = MOTDRenderer.plain(segments)
        if plain.isEmpty {
            plain = edition == .java ? "A Minecraft Server" : "Minecraft Bedrock Server"
            segments = MOTDRenderer.legacySegments(plain)
        }
        return ServerStatus(
            edition: edition, host: address.host, port: targetPort,
            versionName: version.isEmpty ? "未知版本" : version,
            protocolNumber: protocolNumber, online: online,
            maxPlayers: maximum, latencyMs: latency, motdSegments: segments,
            motdPlain: plain, faviconBase64: nil, sample: [], software: "",
            secureChat: false, gameMode: "", subMotd: "", legacy: legacy,
            viaSRV: viaSRV, target: viaSRV ? "\(targetHost):\(targetPort)" : nil
        )
    }

    // MARK: - SRV

    private struct SRVRecord {
        let host: String
        let port: UInt16
    }

    private func resolveSRV(host: String) async -> SRVRecord? {
        guard !host.contains(":"), !host.isEmpty else { return nil }
        let query = "_minecraft._tcp.\(host)"
        let escaped = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        let endpoints = [
            "https://cloudflare-dns.com/dns-query?name=\(escaped)&type=SRV",
            "https://dns.alidns.com/resolve?name=\(escaped)&type=SRV",
            "https://dns.google/resolve?name=\(escaped)&type=SRV",
        ]
        for endpoint in endpoints {
            guard let url = URL(string: endpoint) else { continue }
            var request = URLRequest(url: url)
            request.timeoutInterval = 1.5
            request.setValue("application/dns-json", forHTTPHeaderField: "Accept")
            request.setValue("CubeProbe/1.0 iOS", forHTTPHeaderField: "User-Agent")
            do {
                let (data, response) = try await Self.session.data(for: request)
                guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { continue }
                guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { continue }
                let answers = (root["Answer"] as? [[String: Any]])
                    ?? (root["Answers"] as? [[String: Any]]) ?? []
                var candidates: [(priority: Int, weight: Int, host: String, port: UInt16)] = []
                for answer in answers {
                    guard (answer["type"] as? Int) == 33,
                          let dataString = answer["data"] as? String else { continue }
                    let parts = dataString.split(separator: " ").map(String.init)
                    guard parts.count >= 4,
                          let priority = Int(parts[0]),
                          let weight = Int(parts[1]),
                          let port = UInt16(parts[2]),
                          port > 0 else { continue }
                    let target = parts[3].trimmingCharacters(in: CharacterSet(charactersIn: "."))
                    if !target.isEmpty {
                        candidates.append((priority, max(1, weight), target, port))
                    }
                }
                guard let minPriority = candidates.map(\.priority).min() else { continue }
                let preferred = candidates.filter { $0.priority == minPriority }
                let total = preferred.reduce(0) { $0 + $1.weight }
                var roll = total > 0 ? Int.random(in: 0..<total) : 0
                for candidate in preferred {
                    roll -= candidate.weight
                    if roll < 0 {
                        return SRVRecord(host: candidate.host, port: candidate.port)
                    }
                }
                if let last = preferred.last {
                    return SRVRecord(host: last.host, port: last.port)
                }
            } catch {
                continue
            }
        }
        return nil
    }

    // MARK: - Error mapping

    private func mapConnectionError(_ error: Error) -> QueryError {
        if let queryError = error as? QueryError { return queryError }
        if let nwError = error as? NWError {
            switch nwError {
            case .dns(_):
                return .unknownHost
            case .posix(let code) where code == .ECONNREFUSED:
                return .refused
            case .posix(let code) where code == .ETIMEDOUT:
                return .timeout
            default:
                return .network
            }
        }
        return .protocolError(error.localizedDescription)
    }

    // MARK: - Utilities

    private func millisecondsSince(_ start: Date) -> Int {
        max(1, Int(Date().timeIntervalSince(start) * 1000))
    }

    private func withTimeout<T>(_ seconds: Double,
                                operation: @escaping () async throws -> T) async throws -> T {
        try await withThrowingTaskGroup(of: T.self) { group in
            group.addTask { try await operation() }
            group.addTask {
                try await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
                throw QueryError.timeout
            }
            guard let result = try await group.next() else { throw QueryError.timeout }
            group.cancelAll()
            return result
        }
    }

    private func appendVarInt(_ value: Int32, to data: inout Data) {
        var remaining = UInt32(bitPattern: value)
        repeat {
            var byte = UInt8(remaining & 0x7F)
            remaining >>= 7
            if remaining != 0 { byte |= 0x80 }
            data.append(byte)
        } while remaining != 0
    }

    private func appendUInt16(_ value: UInt16, to data: inout Data) {
        data.append(UInt8((value >> 8) & 0xFF))
        data.append(UInt8(value & 0xFF))
    }

    private func appendUInt64(_ value: UInt64, to data: inout Data) {
        for shift in stride(from: 56, through: 0, by: -8) {
            data.append(UInt8((value >> UInt64(shift)) & 0xFF))
        }
    }

    private func readVarInt(_ data: Data, cursor: inout Int) throws -> Int32 {
        var value: Int32 = 0
        var position = 0
        while true {
            guard cursor < data.count else {
                throw QueryError.protocolError("VarInt 截断")
            }
            let byte = data[cursor]
            cursor += 1
            value |= Int32(byte & 0x7F) << Int32(position)
            if byte & 0x80 == 0 { return value }
            position += 7
            if position >= 35 {
                throw QueryError.protocolError("VarInt 过长")
            }
        }
    }
}

// MARK: - TCP

private final class TCPConnection {
    private let connection: NWConnection
    private var buffer = Data()

    init(host: String, port: UInt16) {
        let endpoint = NWEndpoint.hostPort(
            host: NWEndpoint.Host(host),
            port: NWEndpoint.Port(rawValue: port)!
        )
        connection = NWConnection(to: endpoint, using: .tcp)
    }

    func start() async throws {
        try await MinecraftClient.waitUntilReady(connection)
    }

    func send(_ data: Data) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            connection.send(content: data, completion: .contentProcessed { error in
                if let error {
                    continuation.resume(throwing: QueryError.protocolError(error.localizedDescription))
                } else {
                    continuation.resume()
                }
            })
        }
    }

    func readExactly(_ count: Int) async throws -> Data {
        while buffer.count < count {
            let chunk = try await receiveChunk(max(4096, count - buffer.count))
            guard !chunk.isEmpty else {
                throw QueryError.protocolError("服务器关闭了连接")
            }
            buffer.append(chunk)
        }
        let result = buffer.prefix(count)
        buffer.removeFirst(count)
        return Data(result)
    }

    func readVarInt() async throws -> Int32 {
        var value: Int32 = 0
        var position = 0
        while true {
            let byte = (try await readExactly(1))[0]
            value |= Int32(byte & 0x7F) << Int32(position)
            if byte & 0x80 == 0 { return value }
            position += 7
            if position >= 35 {
                throw QueryError.protocolError("VarInt 过长")
            }
        }
    }

    func close() {
        connection.cancel()
    }

    private func receiveChunk(_ maximum: Int) async throws -> Data {
        DispatchQueue.global().asyncAfter(deadline: .now() + 12) { [weak self] in
            self?.connection.cancel()
        }
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
            connection.receive(minimumIncompleteLength: 1, maximumLength: maximum) {
                data, _, isComplete, error in
                if let error {
                    continuation.resume(throwing: QueryError.timeout)
                } else if let data, !data.isEmpty {
                    continuation.resume(returning: data)
                } else if isComplete {
                    continuation.resume(returning: Data())
                } else {
                    continuation.resume(returning: Data())
                }
            }
        }
    }
}

// MARK: - UDP

private final class UDPConnection {
    private let connection: NWConnection

    init(host: String, port: UInt16) {
        let endpoint = NWEndpoint.hostPort(
            host: NWEndpoint.Host(host),
            port: NWEndpoint.Port(rawValue: port)!
        )
        connection = NWConnection(to: endpoint, using: .udp)
    }

    func start() async throws {
        try await MinecraftClient.waitUntilReady(connection)
    }

    func send(_ data: Data) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            connection.send(content: data, completion: .contentProcessed { error in
                if let error {
                    continuation.resume(throwing: QueryError.protocolError(error.localizedDescription))
                } else {
                    continuation.resume()
                }
            })
        }
    }

    func receive() async throws -> Data {
        DispatchQueue.global().asyncAfter(deadline: .now() + 4.5) { [weak self] in
            self?.connection.cancel()
        }
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
            connection.receiveMessage { data, _, _, error in
                if error != nil {
                    continuation.resume(throwing: QueryError.timeout)
                } else if let data, !data.isEmpty {
                    continuation.resume(returning: data)
                } else {
                    continuation.resume(throwing: QueryError.timeout)
                }
            }
        }
    }

    func close() {
        connection.cancel()
    }
}

extension MinecraftClient {
    fileprivate static func waitUntilReady(_ connection: NWConnection) async throws {
        DispatchQueue.global().asyncAfter(deadline: .now() + 10) {
            connection.cancel()
        }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let lock = NSLock()
            var finished = false
            func finish(_ error: Error?) {
                lock.lock()
                defer { lock.unlock() }
                guard !finished else { return }
                finished = true
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    finish(nil)
                case .failed(let error):
                    finish(mapError(error))
                case .cancelled:
                    finish(QueryError.timeout)
                default:
                    break
                }
            }
            connection.start(queue: networkQueue)
        }
    }

    private static func mapError(_ error: NWError) -> QueryError {
        switch error {
        case .dns(_):
            return .unknownHost
        case .posix(let code) where code == .ECONNREFUSED:
            return .refused
        case .posix(let code) where code == .ETIMEDOUT:
            return .timeout
        default:
            return .network
        }
    }
}
