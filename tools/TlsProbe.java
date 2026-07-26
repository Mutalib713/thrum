import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TlsProbe {
    public static void main(String[] args) {
        String[] urls = {
            "https://repo.maven.apache.org/maven2/junit/junit/4.13.2/junit-4.13.2.pom",
            "https://dl.google.com/dl/android/maven2/androidx/core/core-ktx/1.16.0/core-ktx-1.16.0.pom",
            "https://plugins.gradle.org/m2/",
        };
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        int failures = 0;
        for (String url : urls) {
            try {
                HttpResponse<Void> r = client.send(
                    HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.discarding());
                System.out.println("OK   " + r.statusCode() + "  " + url);
            } catch (Exception e) {
                failures++;
                System.out.println("FAIL " + url);
                System.out.println("     " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        System.out.println(failures == 0 ? "TLS OK" : "TLS BROKEN (" + failures + " of " + urls.length + ")");
        System.exit(failures == 0 ? 0 : 1);
    }
}
