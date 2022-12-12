import java.io.IOException;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

// handler di terminazione del server in caso di spegnimento brutale, chiude tutti i thread e le risorse attive
public class ServerTerminationHandler extends Thread {
    private final int maxDelay;
    private final ExecutorService pool;
    private final ServerSocket serverSocket;
    private final SecretWordSessionManager sessionMenager;
    private final MulticastSocket ms;

    public ServerTerminationHandler(int maxDelay, ExecutorService pool, ServerSocket serverSocket, SecretWordSessionManager sessionMenager, MulticastSocket ms) {
        this.maxDelay = maxDelay;
        this.pool = pool;
        this.serverSocket = serverSocket;
        this.sessionMenager = sessionMenager;
        this.ms = ms;
    }

    public void run() {
        // Avvio la procedura di terminazione del server.
        System.out.println("[SERVER] Avvio terminazione...");
        // Chiudo la ServerSocket in modo tale da non accettare piu' nuove richieste.
        try { // se esiste e non è gia stata chiusa la chiudo
            if (serverSocket != null && !serverSocket.isClosed())
                serverSocket.close();
            if (ms != null && !ms.isClosed())
                ms.close();
        }
        catch (IOException e) {
            System.err.printf("[SERVER] Errore nella chiusura del socket del server: %s\n", e.getMessage());
        }
        // Faccio terminare il pool di thread.
        pool.shutdown();
        try {
            if (!pool.awaitTermination(maxDelay, TimeUnit.MILLISECONDS))
                pool.shutdownNow();
        }
        catch (InterruptedException e) {pool.shutdownNow();}
        // chiudo anche il sessionMenager
        sessionMenager.stopSWSM();
        System.out.println("[SERVER] Terminato.");
    }
}