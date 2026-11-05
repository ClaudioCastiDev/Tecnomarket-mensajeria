package cl.duoc.pedidos360.mensajeria.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Convierte los claims de Cognito en authorities de Spring Security:
 * <ul>
 * <li>claim "scope" (texto separado por espacios) ->
 * SCOPE_..., etc.</li>
 * <li>claim "cognito:groups" (grupos del user pool) -> ROLE_ADMIN,
 * ROLE_CLIENTE, etc.</li>
 * </ul>
 */
public class CognitoAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        Collection<GrantedAuthority> fromScopes = scopeConverter.convert(jwt);
        if (fromScopes != null) {
            authorities.addAll(fromScopes);
        }

        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        if (groups != null) {
            for (String group : groups) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + group.toUpperCase(Locale.ROOT)));
            }
        }
        return authorities;
    }
}