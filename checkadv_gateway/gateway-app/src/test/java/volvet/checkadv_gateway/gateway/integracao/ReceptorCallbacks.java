package volvet.checkadv_gateway.gateway.integracao;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

/** Faz o papel da instância do CheckADV recebendo callbacks (servidor HTTP do JDK). */
class ReceptorCallbacks {

    record Recebido(String caminho, Map<String, String> headers, byte[] corpo) {
        String header(String nome) {
            return headers.get(nome.toLowerCase());
        }
    }

    private final HttpServer servidor;
    private final List<Recebido> recebidos = new CopyOnWriteArrayList<>();
    private final AtomicInteger statusResposta = new AtomicInteger(200);

    ReceptorCallbacks() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            byte[] corpo = troca.getRequestBody().readAllBytes();
            var headers = new java.util.HashMap<String, String>();
            troca.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));
            recebidos.add(new Recebido(troca.getRequestURI().getRawPath(), headers, corpo));
            troca.sendResponseHeaders(statusResposta.get(), -1);
            troca.close();
        });
        servidor.start();
    }

    String url(String caminho) {
        return "http://localhost:" + servidor.getAddress().getPort() + caminho;
    }

    List<Recebido> recebidos() {
        return recebidos;
    }

    void responderCom(int status) {
        statusResposta.set(status);
    }

    void limpar() {
        recebidos.clear();
        statusResposta.set(200);
    }
}
