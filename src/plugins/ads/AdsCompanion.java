package plugins.ads;

import freenet.crypt.DSAPublicKey;
import freenet.keys.CHKBlock;
import freenet.keys.Key;
import freenet.keys.KeyBlock;
import freenet.keys.NodeCHK;
import freenet.keys.NodeSSK;
import freenet.keys.SSKBlock;
import freenet.l10n.PluginL10n;
import freenet.node.LowLevelGetException;
import freenet.node.Node;
import freenet.node.NodeClientCore;
import freenet.node.NodeGetPubkey;
import freenet.node.RequestCompletionListener;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginFCP;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Base64;
import freenet.support.IllegalBase64Exception;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.plugins.helpers1.PluginContext;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

/**
 * @author trem
 * 
 * /home/trem/Arbeitsplatz/Meins/Freenet/ads-companion/nb-project/dist/ADS_Companion_Plugin.jar
 */
public class AdsCompanion implements FredPlugin, FredPluginFCP, FredPluginThreadless, FredPluginVersioned, FredPluginRealVersioned {
    
    static {
        Logger.registerClass(AdsCompanion.class);
    }

    private PluginContext pluginContext;
    private FCPHandler fcpHandler;
    private PluginL10n intl;
    private ServerSocket ss;
    private final List<Socket> clients;
    private volatile boolean terminate;
    private NodeGetPubkey getPubKey;
    
    public AdsCompanion() {
        this.clients = new ArrayList<Socket>();
    }
    
    @Override
    public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
        try {
            fcpHandler.handle(replysender, params, data, accesstype);
        } catch (PluginNotFoundException pnfe) {
            Logger.error(this, "Connection to request sender Lost.", pnfe);
        }
    }
    
    private boolean fetchFromStores(Key key) throws IOException {
        final Node n = pluginContext.clientCore.node;
        final KeyBlock blk = n.fetch(key, true, true, true, false, null);
        
        if (blk == null) {
            return false;
        }
        
        final StringBuilder msgb = new StringBuilder();
        
        if (blk instanceof CHKBlock) {
            final CHKBlock chkBlock = (CHKBlock) blk;
            
            msgb.append("chk ");
            msgb.append(Base64.encode(chkBlock.getRoutingKey()));
            msgb.append(' ');
            msgb.append(Base64.encode(
                    new byte[] {
                        (byte)(chkBlock.getKey().getType() & 0xff)}));
            msgb.append(' ');
            msgb.append(Base64.encode(chkBlock.getHeaders()));
            msgb.append(' ');
            msgb.append(Base64.encode(chkBlock.getData()));
            msgb.append('\n');
        } else if (blk instanceof SSKBlock) {
            final SSKBlock sskBlock = (SSKBlock) blk;
            final DSAPublicKey pk = sskBlock.getPubKey();
            
            msgb.append("ssk ");
            msgb.append(Base64.encode(sskBlock.getRoutingKey()));
            msgb.append(' ');
            msgb.append(Base64.encode(pk.asPaddedBytes()));
            msgb.append(' ');
            msgb.append(Base64.encode(sskBlock.getRawHeaders()));
            msgb.append(' ');
            msgb.append(Base64.encode(sskBlock.getRawData()));
            msgb.append('\n');
        }
        
        final String msg = msgb.toString();
        
        sendToClients(msg);
        
        return true;
    }
    
    private void sendToClients(final String msg) throws IOException {
        for (Socket skt : this.clients) {
            synchronized (skt) {
                try {
                    final OutputStreamWriter osw = new OutputStreamWriter(skt.getOutputStream());
                    osw.write(msg);
                    osw.flush();
                } catch (IOException e) {
                    skt.close();
                }
            }
        }
        
        final Iterator<Socket> i = this.clients.iterator();
        
        while (i.hasNext()) {
            final Socket skt = i.next();
            
            if (skt.isClosed()) {
                i.remove();
            }
        }
    }
    
    private void schedGet(final Key k) throws IOException {
        if (!fetchFromStores(k)) {
            final NodeClientCore cc = pluginContext.clientCore;
            RequestCompletionListener l = new RequestCompletionListener() {

                @Override
                public void onSucceeded() {
                    System.out.println("SUCCESS");

                    try {
                        fetchFromStores(k);
                    } catch (IOException ex) {
                        java.util.logging.Logger.getLogger(AdsCompanion.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                @Override
                public void onFailed(LowLevelGetException e) {
                    System.out.println("OH NOES: " + e);
                }
            };

            cc.asyncGet(k, false, l, true, true, true, false, false);
        }
    }
    
    @Override
    public void runPlugin(PluginRespirator pr) {
        this.terminate = false;
        
        pluginContext = new PluginContext(pr);
        fcpHandler = new FCPHandler(pluginContext);
        
        final Node node = pluginContext.node;
        final Class<? extends Node> nclass = node.getClass();
        
        try {
            final Field gpkField = nclass.getDeclaredField("getPubKey");
            gpkField.setAccessible(true);
            final Object gpk = gpkField.get(node);
            this.getPubKey = (NodeGetPubkey) gpk;
        } catch (Exception ex) {
            System.out.println("failed to access getpubkey: " + ex);
        }
        
        try {
            this.ss = this.listen();
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(AdsCompanion.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void terminate() {
        fcpHandler.kill();
        fcpHandler = null;
        this.terminate = true;
        
        if (this.ss != null) {
            try {
                this.ss.close();
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(AdsCompanion.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            this.ss = null;
        }
        
        for (Socket s : this.clients) {
            try {
                s.close();
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(AdsCompanion.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        this.clients.clear();
    }

    @Override
    public String getVersion() {
        return Version.longVersionString;
    }

    @Override
    public long getRealVersion() {
        return Version.version;
    }
    
    private ServerSocket listen() throws IOException {
        
        final int port = this.pluginContext.node.getOpennetFNPPort() + 1;
        
        final ServerSocket sskt = new ServerSocket(
                port, 0, InetAddress.getByName("127.0.0.1"));
        
        System.out.println("ADS COMPANION listening on port " + port);
        
        new Thread(new Acceptor()).start();
        
        return sskt;
    }
    
    private void createClient(Socket skt) throws IOException {
        final Thread thread = new Thread(new RequestHandler(skt));
        thread.start();
        this.clients.add(skt);
    }
    
    private class RequestHandler implements Runnable {
        
        private final Socket skt;
        private final BufferedReader reader;

        public RequestHandler(Socket skt) throws IOException {
            this.skt = skt;
            this.reader = new BufferedReader(
                    new InputStreamReader(skt.getInputStream()));
        }
        
        @Override
        public void run() {
            while (!terminate) {
                try {
                    handleRequest();
                } catch (Exception ex) {
                    java.util.logging.Logger.getLogger(AdsCompanion.class.getName()).log(Level.SEVERE, null, ex);
                    try {
                        skt.close();
                    } catch (IOException ex1) {
                        java.util.logging.Logger.getLogger(AdsCompanion.class.getName()).log(Level.SEVERE, null, ex1);
                    }
                    return;
                }
            }
            
        }

        private void handleRequest() throws IOException, IllegalBase64Exception {
            final String req = this.reader.readLine();
            
            if (req == null) {
                throw new EOFException();
            }
            
            System.out.println("REQ: " + req);
            
            final String[] parts = req.split(" ");
            
            if ("getchk".equals(parts[0])) {
                final byte[] key = Base64.decode(parts[1]);
                final byte ca = Byte.parseByte(parts[2]);
                final NodeCHK chk = new NodeCHK(key, ca);
                schedGet(chk);
            } else if("getssk".equals(parts[0])) {
                final byte[] hpk = Base64.decode(parts[1]);
                final byte[] ehd = Base64.decode(parts[2]);
                final byte ca = Byte.parseByte(parts[3]);
                final NodeSSK nodeSSK = new NodeSSK(hpk, ehd, ca);
                schedGet(nodeSSK);
            } else {
                System.out.println("what's " + parts[0]);
            }
            
        }
        
    }
    
    private class Acceptor implements Runnable {

        @Override
        public void run() {
            while (!terminate) {
                try {
                    final Socket skt = ss.accept();
                    createClient(skt);
                } catch (IOException ex) {
                    java.util.logging.Logger.getLogger(AdsCompanion.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        
    }
    
}
