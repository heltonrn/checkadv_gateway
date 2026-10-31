package volvet.checkadv_gateway.gateway.seguranca;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import tools.jackson.databind.json.JsonMapper;
import volvet.checkadv_gateway.gateway.config.HmacProperties;

/**
 * Cadeias de segurança, da mais específica para a mais geral:
 * <ol>
 *   <li>{@code /v1/**} — HMAC com chave de GRUPO</li>
 *   <li>{@code /admin/**} — HMAC com chave de OPERADOR (além disso, só rede interna)</li>
 *   <li>{@code /webhook/**} — aberto para a Meta; a assinatura X-Hub-Signature-256 será
 *       conferida por um filtro próprio (próxima etapa)</li>
 *   <li>{@code /actuator/**} — health e prometheus abertos; expor só na rede interna</li>
 *   <li>resto — negado</li>
 * </ol>
 * Tudo stateless, sem sessão, sem CSRF (não há navegador nem cookie).
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(HmacProperties.class)
public class SecurityConfig {

    @Bean
    RespostaErroJson respostaErroJson(JsonMapper mapper) {
        return new RespostaErroJson(mapper);
    }

    @Bean
    @Order(1)
    SecurityFilterChain cadeiaApiV1(HttpSecurity http, RepositorioChavesHmac chaves, Clock relogio,
                                    HmacProperties props, RespostaErroJson resposta) throws Exception {
        return cadeiaHmac(http.securityMatcher("/v1/**"), TipoChave.GRUPO, chaves, relogio, props, resposta);
    }

    @Bean
    @Order(2)
    SecurityFilterChain cadeiaAdmin(HttpSecurity http, RepositorioChavesHmac chaves, Clock relogio,
                                    HmacProperties props, RespostaErroJson resposta) throws Exception {
        return cadeiaHmac(http.securityMatcher("/admin/**"), TipoChave.OPERADOR, chaves, relogio, props, resposta);
    }

    /**
     * A Meta não usa nossas chaves HMAC; ela assina com o App Secret. Enquanto o filtro
     * dessa assinatura não existe, os endpoints só respondem 501 e não processam nada.
     */
    @Bean
    @Order(3)
    SecurityFilterChain cadeiaWebhook(HttpSecurity http) throws Exception {
        semEstado(http.securityMatcher("/webhook/**"))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain cadeiaActuator(HttpSecurity http) throws Exception {
        semEstado(http.securityMatcher("/actuator/**"))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @Order(5)
    SecurityFilterChain cadeiaPadrao(HttpSecurity http, RespostaErroJson resposta) throws Exception {
        semEstado(http)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> resposta.naoAutenticado(res))
                        .accessDeniedHandler((req, res, ex) -> resposta.semPermissao(res)));
        return http.build();
    }

    /**
     * Evita o InMemoryUserDetailsManager com senha gerada que o Spring Boot cria
     * quando não há nenhum UserDetailsService. Aqui não existe login por usuário.
     */
    @Bean
    UserDetailsService semUsuarios() {
        return username -> {
            throw new UsernameNotFoundException("Sem usuários: autenticação apenas por HMAC");
        };
    }

    private SecurityFilterChain cadeiaHmac(HttpSecurity http, TipoChave tipo, RepositorioChavesHmac chaves,
                                           Clock relogio, HmacProperties props, RespostaErroJson resposta)
            throws Exception {
        var filtro = new FiltroAutenticacaoHmac(tipo, chaves, relogio,
                props.janelaSegundos(), props.corpoMaximoBytes(), resposta);
        semEstado(http)
                .addFilterBefore(filtro, AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().hasRole(tipo.name()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> resposta.naoAutenticado(res))
                        .accessDeniedHandler((req, res, ex) -> resposta.semPermissao(res)));
        return http.build();
    }

    private static HttpSecurity semEstado(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .requestCache(r -> r.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }
}
