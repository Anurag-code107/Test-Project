package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.service.xtrm.XtrmApiClient.CreateUserCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which account creates the user.
 *
 * <p>This is the whole feature in one field: XTRM binds the new user to the {@code IssuerAccountNumber} in
 * the request, and to the account the bearer token belongs to. Send the platform's and the seller can never
 * be paid by their company — permanently, because the email cannot be reused.</p>
 */
class XtrmApiClientImplCreateUserIssuerTest {

    private String capturedIssuer;
    private XtrmCredentials capturedCredentials;

    private XtrmApiClientImpl clientCapturing() {
        return new XtrmApiClientImpl() {
            @Override
            protected Map<?, ?> post(String path, Map<String, Object> body, XtrmCredentials credentials) {
                Map<?, ?> outer = (Map<?, ?>) body.get("CreateUser");
                Map<?, ?> request = (Map<?, ?>) outer.get("request");
                capturedIssuer = String.valueOf(request.get("IssuerAccountNumber"));
                capturedCredentials = credentials;
                return Map.of("CreateUserResponse", Map.of("CreateUserResult", Map.of(
                        "UserID", "PAT26241022",
                        "AccountIdentityLevel", "Basic",
                        "OperationStatus", Map.of("Success", true, "Errors", List.of()))));
            }
        };
    }

    private CreateUserCommand command() {
        return new CreateUserCommand("Probe", "Seller", "probe@acme.test", "4085556247", "US",
                "1 Market St", null, "San Francisco", "CA", "94105", "US");
    }

    private XtrmCredentials company() {
        return new XtrmCredentials("company-id", "secret", "SPN26241004", "206415", "2314");
    }

    @Test
    void sendsTheSuppliedAccountAsIssuer() {
        XtrmApiClientImpl client = clientCapturing();
        client.createUser(command(), company());

        // Both must be the company's: the account named in the body, and the token the call is made with.
        // XTRM binds the new user to that pairing, and it cannot be undone.
        assertThat(capturedIssuer).isEqualTo("SPN26241004");
        assertThat(capturedCredentials).isEqualTo(company());
    }

    @Test
    void returnsThePatFromTheResponse() {
        assertThat(clientCapturing().createUser(command(), company()).recipientUserId())
                .isEqualTo("PAT26241022");
    }

    @Test
    void reportsSuccessFromTheResponse() {
        assertThat(clientCapturing().createUser(command(), company()).success()).isTrue();
    }
}
