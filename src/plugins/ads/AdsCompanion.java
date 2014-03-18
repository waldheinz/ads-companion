package plugins.ads;

import freenet.keys.CHKBlock;
import freenet.keys.Key;
import freenet.keys.NodeCHK;
import freenet.l10n.PluginL10n;
import freenet.node.LowLevelGetException;
import freenet.node.Node;
import freenet.node.NodeClientCore;
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
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.plugins.helpers1.PluginContext;
import java.io.IOException;
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

    @Override
    public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
        try {
            fcpHandler.handle(replysender, params, data, accesstype);
        } catch (PluginNotFoundException pnfe) {
            Logger.error(this, "Connection to request sender Lost.", pnfe);
        }
    }
    
    private CHKBlock fetchFromStores(NodeCHK key) throws IOException {
        final Node n = pluginContext.clientCore.node;
        
        return n.fetch(key, true, true, true, true, true, null);
        /*
        final CHKStore[] stores = new CHKStore[] {
            n.getChkDatacache(), n.getChkDatastore()
        };
        
        for (CHKStore s : stores) {
            final CHKBlock blk = s.fetch(key, false, true, null);
            
            if (blk != null) {
                return blk;
            }
        }
        
        return null;
                */
    }
    
    private void schedGet(final NodeCHK k) {
        final NodeClientCore cc = pluginContext.clientCore;
        RequestCompletionListener l = new RequestCompletionListener() {
            
            @Override
            public void onSucceeded() {
                System.out.println("SUCCESS!!!!!!!!!!!!!!!!!!!!");
                
                try {
                    CHKBlock fetched = fetchFromStores(k);
                    
                    if (fetched != null) {
                        System.out.println(fetched.getData().length);
                    }
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
    
    @Override
    public void runPlugin(PluginRespirator pr) {

        Logger.minor(this, "Initialising Key Utils.");
        
        pluginContext = new PluginContext(pr);
        
        try {
            schedGet(new NodeCHK(
                    Base64.decode("f9KRS95Gf84qqmeyIGg2Y1Tqy84TjR7Fz9JrX~lEtyM")
                    , Key.ALGO_AES_CTR_256_SHA256));
            
        } catch (Exception ex) {
            Logger.error(this, "sdfsdf", ex);
        }
        
        fcpHandler = new FCPHandler(pluginContext);

        Logger.minor(this, "Initialising Key Utils done.");
    }

    @Override
    public void terminate() {
        // TODO kill all 'session handles'
        // TODO kill all requests
        fcpHandler.kill();
        fcpHandler = null;
    }

    @Override
    public String getVersion() {
        return Version.longVersionString;
    }

    @Override
    public long getRealVersion() {
        return Version.version;
    }

}
