import SwiftUI

struct PracticeQuestion: Codable, Identifiable {
    let category: String
    let question: String
    let options: [String]
    let correctIndex: Int
    let explanation: String
    let reference: String

    var id: String { category + ":" + question }
}

private enum PracticeBank {
    static let all: [PracticeQuestion] = {
        guard let url = Bundle.main.url(forResource: "question-bank", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let values = try? JSONDecoder().decode([PracticeQuestion].self, from: data) else {
            return []
        }
        return values.filter { $0.options.count == 4 && (0..<4).contains($0.correctIndex) }
    }()

    static func daily(date: Date = .now) -> [PracticeQuestion] {
        let day = DateFormatter.localizedString(from: date, dateStyle: .short, timeStyle: .none)
        let grouped = Dictionary(grouping: all, by: \.category)
        // Stable daily ordering avoids losing an answer when the quiz screen is reopened.
        return grouped.keys.sorted().flatMap { category in
            Array((grouped[category] ?? []).sorted {
                stableScore($0.id + day) < stableScore($1.id + day)
            }.prefix(5))
        }
    }

    private static func stableScore(_ text: String) -> UInt64 {
        var hash: UInt64 = 14_695_981_039_346_656_037
        for byte in text.utf8 {
            hash = (hash ^ UInt64(byte)) &* 1_099_511_628_211
        }
        return hash
    }
}

struct PracticeQuizView: View {
    @State private var index = 0
    @State private var answers: [String: Int] = [:]

    private let questions = PracticeBank.daily()

    private var dateKey: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return "ewb.ios.quiz." + formatter.string(from: .now)
    }

    private var question: PracticeQuestion? {
        guard !questions.isEmpty && questions.indices.contains(index) else { return nil }
        return questions[index]
    }

    private var correct: Int {
        questions.filter { answers[$0.id] == $0.correctIndex }.count
    }

    private var answered: Int {
        questions.filter { answers[$0.id] != nil }.count
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 15) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("309A · CEC 2024 study")
                        .font(.caption.bold())
                        .foregroundStyle(WorkbenchStyle.cyan)
                    Text("Daily practice quiz")
                        .font(.title2.bold())
                    Text("Five questions from each of the five task categories. Answer, review the worked explanation, then continue.")
                        .font(.subheadline)
                        .foregroundStyle(WorkbenchStyle.muted)
                        .fixedSize(horizontal: false, vertical: true)
                    if !questions.isEmpty {
                        ProgressView(value: Double(answered), total: Double(questions.count))
                            .tint(WorkbenchStyle.cyan)
                        Text("Answered: \(answered)/\(questions.count)   •   Correct: \(correct)")
                            .font(.subheadline.monospacedDigit())
                    }
                }
                .padding(15)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 14))

                if let question {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("QUESTION \(index + 1) OF \(questions.count)")
                            .font(.caption.bold())
                            .foregroundStyle(WorkbenchStyle.cyan)
                        Text(question.category)
                            .font(.caption)
                            .foregroundStyle(WorkbenchStyle.muted)
                        Text(question.question)
                            .font(.body.weight(.semibold))
                            .fixedSize(horizontal: false, vertical: true)

                        ForEach(question.options.indices, id: \.self) { option in
                            let selected = answers[question.id] == option
                            let revealed = answers[question.id] != nil
                            Button {
                                guard !revealed else { return }
                                answers[question.id] = option
                                UserDefaults.standard.set(answers, forKey: dateKey)
                                NotificationCenter.default.post(name: .ewbLocalRecordChanged, object: "quizHistory")
                            } label: {
                                HStack(alignment: .top, spacing: 10) {
                                    Text("\(String(UnicodeScalar(65 + option)!)).")
                                        .font(.subheadline.bold())
                                    Text(question.options[option])
                                        .font(.subheadline)
                                        .multilineTextAlignment(.leading)
                                        .fixedSize(horizontal: false, vertical: true)
                                    Spacer(minLength: 2)
                                    if revealed && option == question.correctIndex {
                                        Image(systemName: "checkmark.circle.fill")
                                            .foregroundStyle(.green)
                                    } else if selected {
                                        Image(systemName: "xmark.circle.fill")
                                            .foregroundStyle(.orange)
                                    }
                                }
                                .foregroundStyle(.white)
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(selected ? WorkbenchStyle.surface : WorkbenchStyle.background,
                                            in: RoundedRectangle(cornerRadius: 10))
                                .overlay(RoundedRectangle(cornerRadius: 10)
                                    .stroke(selected ? WorkbenchStyle.cyan : WorkbenchStyle.border,
                                            lineWidth: selected ? 2 : 1))
                            }
                            .disabled(revealed)
                            .buttonStyle(.plain)
                        }

                        if let choice = answers[question.id] {
                            VStack(alignment: .leading, spacing: 6) {
                                Label(choice == question.correctIndex ? "Correct answer" : "Review this answer",
                                      systemImage: choice == question.correctIndex ? "checkmark.seal" : "book")
                                    .font(.subheadline.bold())
                                    .foregroundStyle(choice == question.correctIndex ? .green : .orange)
                                Text("Answer: \(question.options[question.correctIndex])")
                                    .font(.subheadline.bold())
                                Text(question.explanation)
                                    .font(.subheadline)
                                    .fixedSize(horizontal: false, vertical: true)
                                Text("Reference: \(question.reference)")
                                    .font(.caption)
                                    .foregroundStyle(WorkbenchStyle.muted)
                            }
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(WorkbenchStyle.background, in: RoundedRectangle(cornerRadius: 10))
                        }

                        HStack(spacing: 10) {
                            Button("Previous") {
                                index = max(0, index - 1)
                            }
                            .disabled(index == 0)
                            .buttonStyle(.bordered)

                            Spacer()

                            Button(index == questions.count - 1 ? "Review first" : "Next question") {
                                index = index == questions.count - 1 ? 0 : index + 1
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(WorkbenchStyle.cyan)
                            .foregroundStyle(WorkbenchStyle.background)
                        }
                    }
                    .padding(15)
                    .background(WorkbenchStyle.surface, in: RoundedRectangle(cornerRadius: 14))
                } else {
                    Text("The question bank could not be loaded. Install a current app build and try again.")
                        .font(.subheadline)
                        .foregroundStyle(.orange)
                }

                Text("Study aid only. Questions are practice items, not official Red Seal examination questions. Confirm current CEC requirements in the licensed Code.")
                    .font(.caption)
                    .foregroundStyle(WorkbenchStyle.muted)

                if answered > 0 {
                    Button("Reset today's quiz", role: .destructive) {
                        answers.removeAll()
                        index = 0
                        UserDefaults.standard.removeObject(forKey: dateKey)
                        NotificationCenter.default.post(name: .ewbLocalRecordChanged, object: "quizHistory")
                    }
                    .font(.footnote)
                }
            }
            .padding(14)
            .frame(maxWidth: 740)
            .frame(maxWidth: .infinity)
        }
        .background(WorkbenchStyle.background)
        .navigationTitle("Daily quiz")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            answers = UserDefaults.standard.dictionary(forKey: dateKey) as? [String: Int] ?? [:]
        }
    }
}
