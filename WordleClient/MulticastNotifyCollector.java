import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

// thread lanciato in parallelo dal client per rimanere in ascolto di eventuali notifiche dagli altri giocatori
public class MulticastNotifyCollector extends Thread {
    public static String MUL_ADDR;
    public static int MUL_PORT;
    List<String> notifiche; // lista di notifiche passata per riferimento dal client da modificare ogni volta che ne arriva una nuova
    String username; // usato per vedere se il messaggio è quello di terminazione

    public MulticastNotifyCollector(List<String> notifiche, String username, String address, int port) {
        this.notifiche = notifiche;
        this.username = username;
        MUL_ADDR = address;
        MUL_PORT = port;
        this.start();
    }

    @Override
    public void run() {
        try {
            // inizializzo la connessione sull'indirizzo multicast
            MulticastSocket ms = new MulticastSocket(MUL_PORT);
            InetSocketAddress ia= new InetSocketAddress(MUL_ADDR, MUL_PORT);
            NetworkInterface netIf = NetworkInterface.getByName("en0");
            ms.joinGroup(ia, netIf);
            // attacco il termination handler
            Runtime.getRuntime().addShutdownHook(new MNCTerminationHandler(ms, ia, netIf));
            // ricevo notifiche finchè non ne arrriva una particolare con username+&%#$ quando l'utente fa il logout
            while (true) {
                // ricevo il pacchetto
                byte[] buffer = new byte[1024];
                DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
                ms.receive(receivedPacket);
                // aggiungo la notifica alla lista
                String notifica = new String(receivedPacket.getData(),0, receivedPacket.getLength(), StandardCharsets.US_ASCII);
                //System.out.println("Ricevuto una notifica: " + notifica);
                if (notifica.equals(this.username+"&%#$")) // messaggio mandato per interrompere questo MNC specifico
                    break;
                else if (!notifica.contains("&%#$") && !notifica.contains(username+",")) //registro la notifica se non contiene il codice di terminazione per un altro client e se non è una mia notifica
                    this.notifiche.add(notifica);

            }
            ms.leaveGroup(ia, netIf);
            ms.close();
        } catch (IOException e) {System.out.println("Errore all' interno del MNC: " + e.getMessage());}
    }
    private static class MNCTerminationHandler extends Thread { // termination handler nel caso il client chiuda l'applicazione brutalmente
        private final MulticastSocket ms;
        private final InetSocketAddress ia;
        private final NetworkInterface netIf;
        public MNCTerminationHandler(MulticastSocket ms, InetSocketAddress ia, NetworkInterface netIf) {
            this.ms = ms;
            this.ia = ia;
            this. netIf = netIf;
        }

        @Override
        public void run() {
            try {
                if (!ms.isClosed()) {
                    ms.leaveGroup(ia, netIf);
                    ms.close();
                }
            } catch (IOException e) { System.out.println("Errore nella chiusura del MNC: " + e.getMessage());}
        }
    }


}
