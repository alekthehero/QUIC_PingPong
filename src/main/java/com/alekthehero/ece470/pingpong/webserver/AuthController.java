package com.alekthehero.ece470.pingpong.webserver;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.AuthFlowType;
import com.amazonaws.services.cognitoidp.model.InitiateAuthRequest;
import com.amazonaws.services.cognitoidp.model.SignUpRequest;
import com.amazonaws.services.cognitoidp.model.SignUpResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private AWSCognitoIdentityProvider cognitoClient =  AWSCognitoIdentityProviderClientBuilder.standard()
            .withRegion(Regions.US_EAST_1)
            .build();
    private final String clientId = "4bhohvu403o13pdb345r7a9hsh";
    
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody UserCredentials credentials) {
        if (credentials.getUsername() == null || credentials.getUsername().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Username cannot be empty");
        }
        
        if (credentials.getPassword() == null || credentials.getPassword().length() < 6) {
            return ResponseEntity.badRequest().body("Password must be at least 6 characters long");
        }

        SignUpRequest signUpRequest = new SignUpRequest()
                .withClientId(clientId)
                .withUsername(credentials.getUsername())
                .withPassword(credentials.getPassword());

        SignUpResult res = cognitoClient.signUp(signUpRequest)
                .withUserConfirmed(true);

        return ResponseEntity.ok("Account Created");
    }
    
    @PostMapping("/signin")
    public ResponseEntity<String> signin(@RequestBody UserCredentials credentials) {

        InitiateAuthRequest initiateAuthRequest = new InitiateAuthRequest()
                .withClientId(clientId)
                .withAuthFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .addAuthParametersEntry("USERNAME", credentials.getUsername())
                .addAuthParametersEntry("PASSWORD", credentials.getPassword());
        String accessToken = cognitoClient.initiateAuth(initiateAuthRequest)
                .getAuthenticationResult()
                .getAccessToken();

        return ResponseEntity.ok(accessToken);
    }
    
    // Simple DTO for user credentials
    public static class UserCredentials {
        private String username;
        private String password;
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
    }
}
