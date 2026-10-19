import Foundation

enum MOTDRenderer {

    struct Style {
        var colorHex: String?
        var bold = false
        var italic = false
        var underline = false
        var strike = false
    }

    private static let palette: [Character: String] = [
        "0": "#9AA7B4", "1": "#7C8CFF", "2": "#4ADE80", "3": "#2DD4BF",
        "4": "#FB7185", "5": "#C084FC", "6": "#FBBF24", "7": "#A3B0BF",
        "8": "#7C8C99", "9": "#60A5FA", "a": "#4ADE80", "b": "#67E8F9",
        "c": "#F87171", "d": "#F0ABFC", "e": "#FDE047", "f": "#F8FAFC",
    ]

    private static let namedColors: [String: String] = [
        "black": "#9AA7B4", "dark_blue": "#7C8CFF", "dark_green": "#4ADE80",
        "dark_aqua": "#2DD4BF", "dark_red": "#FB7185", "dark_purple": "#C084FC",
        "gold": "#FBBF24", "gray": "#A3B0BF", "dark_gray": "#7C8C99",
        "blue": "#60A5FA", "green": "#4ADE80", "aqua": "#67E8F9",
        "red": "#F87171", "light_purple": "#F0ABFC", "yellow": "#FDE047",
        "white": "#F8FAFC",
    ]

    static func segments(from value: Any?) -> [MOTDSegment] {
        var output: [MOTDSegment] = []
        appendJSON(value, style: Style(), depth: 0, into: &output)
        return merge(output)
    }

    static func legacySegments(_ text: String, base: Style = Style()) -> [MOTDSegment] {
        var output: [MOTDSegment] = []
        appendLegacy(text, base: base, into: &output)
        return merge(output)
    }

    static func plain(_ segments: [MOTDSegment]) -> String {
        segments.map(\.text).joined().trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func appendJSON(_ value: Any?, style: Style, depth: Int,
                                   into output: inout [MOTDSegment]) {
        guard let value, depth <= 16 else { return }
        if let string = value as? String {
            appendLegacy(string, base: style, into: &output)
            return
        }
        if let array = value as? [Any] {
            for item in array {
                appendJSON(item, style: style, depth: depth + 1, into: &output)
            }
            return
        }
        guard let object = value as? [String: Any] else { return }

        var nodeStyle = style
        if let color = object["color"] as? String {
            if color.lowercased() == "reset" || color.isEmpty {
                nodeStyle.colorHex = nil
            } else if let parsed = parseColor(color) {
                nodeStyle.colorHex = parsed
            }
        }
        if let bold = object["bold"] as? Bool { nodeStyle.bold = bold }
        if let italic = object["italic"] as? Bool { nodeStyle.italic = italic }
        if let underlined = object["underlined"] as? Bool { nodeStyle.underline = underlined }
        if let strike = object["strikethrough"] as? Bool { nodeStyle.strike = strike }

        if let text = object["text"] as? String, !text.isEmpty {
            appendLegacy(text, base: nodeStyle, into: &output)
        }
        if let extra = object["extra"] as? [Any] {
            for child in extra {
                appendJSON(child, style: nodeStyle, depth: depth + 1, into: &output)
            }
        }
    }

    private static func appendLegacy(_ text: String, base: Style,
                                     into output: inout [MOTDSegment]) {
        let characters = Array(text)
        var index = 0
        var current = base
        while index < characters.count {
            let character = characters[index]
            if character == "\u{00A7}", index + 1 < characters.count {
                let code = characters[index + 1].lowercased().first ?? characters[index + 1]
                if code == "x" {
                    if let parsed = parseHexSequence(characters, start: index + 2) {
                        current.colorHex = parsed
                        index += 14
                        continue
                    }
                }
                if applyCode(code, to: &current, base: base) {
                    index += 2
                    continue
                }
                index += 1
                continue
            }
            var end = index
            while end < characters.count, characters[end] != "\u{00A7}" {
                end += 1
            }
            if end > index {
                output.append(MOTDSegment(
                    text: String(characters[index..<end]),
                    colorHex: current.colorHex,
                    bold: current.bold,
                    italic: current.italic,
                    underline: current.underline,
                    strike: current.strike
                ))
            }
            index = end
        }
    }

    @discardableResult
    private static func applyCode(_ code: Character, to style: inout Style,
                                  base: Style) -> Bool {
        if let color = palette[code] {
            style.colorHex = color
            return true
        }
        switch code {
        case "k":
            return true
        case "l":
            style.bold = true
            return true
        case "m":
            style.strike = true
            return true
        case "n":
            style.underline = true
            return true
        case "o":
            style.italic = true
            return true
        case "r":
            style = base
            return true
        default:
            return false
        }
    }

    private static func parseHexSequence(_ characters: [Character], start: Int) -> String? {
        guard start + 12 <= characters.count else { return nil }
        var digits = ""
        for offset in 0..<6 {
            let position = start + offset * 2
            guard characters[position] == "\u{00A7}",
                  characters[position + 1].isHexDigit else {
                return nil
            }
            digits.append(characters[position + 1])
        }
        return "#" + digits.uppercased()
    }

    private static func parseColor(_ value: String) -> String? {
        let lowered = value.lowercased()
        if let named = namedColors[lowered] { return named }
        guard lowered.hasPrefix("#") else { return nil }
        var hex = String(lowered.dropFirst())
        if hex.count == 3 {
            hex = hex.map { "\($0)\($0)" }.joined()
        }
        guard hex.count == 6, hex.allSatisfy({ $0.isHexDigit }) else { return nil }
        return "#" + hex.uppercased()
    }

    private static func merge(_ segments: [MOTDSegment]) -> [MOTDSegment] {
        var merged: [MOTDSegment] = []
        for segment in segments {
            if var last = merged.last, sameStyle(last, segment) {
                last.text += segment.text
                merged[merged.count - 1] = last
            } else if !segment.text.isEmpty {
                merged.append(segment)
            }
        }
        return merged
    }

    private static func sameStyle(_ a: MOTDSegment, _ b: MOTDSegment) -> Bool {
        a.colorHex == b.colorHex && a.bold == b.bold && a.italic == b.italic
            && a.underline == b.underline && a.strike == b.strike
    }
}
