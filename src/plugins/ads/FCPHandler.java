/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.ads;

import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.plugins.helpers1.AbstractFCPHandler;
import freenet.support.plugins.helpers1.PluginContext;

public class FCPHandler extends AbstractFCPHandler {

	FCPHandler(PluginContext pluginContext2) {
		super(pluginContext2);
	}

	/**
	 * @param accesstype
	 * @throws PluginNotFoundException 
	 */
	@Override
	public void handle(PluginReplySender replysender, String command, String identifier, SimpleFieldSet params, Bucket data, int accesstype) throws PluginNotFoundException {
		if (params == null) {
			sendError(replysender, 0, identifier, "Got void message");
			return;
		}

		if (data != null) {
			sendError(replysender, 0, identifier, "Got a diatribe piece of writing. Data not allowed!");
			return;
		}

                sendError(replysender, 1, identifier, "Unknown command: " + command);
	}

	public void kill() {
	}
}
