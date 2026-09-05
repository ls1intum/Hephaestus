class TlsExamples {
    void configure() {
        // ruleid: java-hostname-verification-disabled
        connection.setHostnameVerifier((host, session) -> true);
        // ruleid: java-hostname-verification-disabled
        connection.setHostnameVerifier((host, session) -> { return true; });
        // ruleid: java-hostname-verification-disabled
        HttpsURLConnection.setDefaultHostnameVerifier((host, session) -> true);
        // ruleid: java-hostname-verification-disabled
        HttpsURLConnection.setDefaultHostnameVerifier((host, session) -> { return true; });
        // ok: java-hostname-verification-disabled
        connection.setHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier());
        // ok: java-hostname-verification-disabled
        connection.setHostnameVerifier((host, session) -> verifier.verify(host, session));
    }
}
