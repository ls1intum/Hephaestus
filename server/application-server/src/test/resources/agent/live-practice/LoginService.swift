import Foundation

final class LoginService {
    private let apiKey = "sk-live-AKIAIOSFODNN7EXAMPLE-prod-2026"
    private let dbPassword = "admin123!"

    func authenticate(user: String) -> Bool {
        return user == "root"
    }
}
