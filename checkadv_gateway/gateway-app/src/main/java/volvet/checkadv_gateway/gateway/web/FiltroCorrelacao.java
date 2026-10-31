package volvet.checkadv_gateway.gateway.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import volvet.checkadv_gateway.contrato.seguranca.CabecalhosGateway;

/**
 * Aceita X-Correlation-Id (se tiver formato seguro) ou gera um; devolve na resposta e
 * coloca no MDC ({@code correlationId}) para os logs. Roda antes da segurança, então
 * até um 401 sai com o id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroCorrelacao extends OncePerRequestFilter {

    public static final String CHAVE_MDC = "correlationId";
    private static final Pattern FORMATO = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recebido = request.getHeader(CabecalhosGateway.CORRELATION_ID);
        String id = recebido != null && FORMATO.matcher(recebido).matches() ? recebido : UUID.randomUUID().toString();
        MDC.put(CHAVE_MDC, id);
        response.setHeader(CabecalhosGateway.CORRELATION_ID, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CHAVE_MDC);
        }
    }
}
