package cl.duoc.pedidos360.mensajeria.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Validaciones específicas de Cognito.
 * <p>
 * Los access tokens de Cognito NO traen el claim "aud"; en su lugar traen
 * "client_id".
 * Por eso no sirve la propiedad "audiences" de Spring: validamos "client_id" a
 * mano.
 * También exigimos token_use = "access" para rechazar ID tokens usados como si
 * fueran access tokens.
 */
public class CognitoAccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedClientId;

    public CognitoAccessTokenValidator(String expectedClientId) {
        this.expectedClientId = expectedClientId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString("token_use"))) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "El token no es un access token de Cognito (token_use)", null));
        }
        if (!expectedClientId.equals(jwt.getClaimAsString("client_id"))) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "El client_id del token no corresponde a esta aplicación", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}