import Foundation

final class LoginService {
    private let password = "admin123!"

    func authenticate(user: String, password: String) -> Bool {
        return user == "root" && password == self.password
    }
}
