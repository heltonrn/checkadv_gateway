package volvet.checkadv_gateway.gateway.seguranca;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Requisição com o corpo já lido em memória. Necessária porque a assinatura é
 * calculada sobre os bytes brutos ANTES do parse, e o controller precisa lê-los de novo.
 */
class RequisicaoComCorpo extends HttpServletRequestWrapper {

    private final byte[] corpo;

    RequisicaoComCorpo(HttpServletRequest request, byte[] corpo) {
        super(request);
        this.corpo = corpo;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream origem = new ByteArrayInputStream(corpo);
        return new ServletInputStream() {
            @Override
            public int read() {
                return origem.read();
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return origem.read(b, off, len);
            }

            @Override
            public boolean isFinished() {
                return origem.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("Leitura assíncrona não suportada");
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        String encoding = getCharacterEncoding();
        Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    @Override
    public int getContentLength() {
        return corpo.length;
    }

    @Override
    public long getContentLengthLong() {
        return corpo.length;
    }
}
