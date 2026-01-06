/*
 * SplitsTreeServer.java Copyright (C) 2024 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.tools.server;


import javafx.application.Application;
import javafx.stage.Stage;
import jloda.fx.util.ArgsOptions;
import jloda.fx.util.ProgramProperties;
import jloda.util.Basic;
import jloda.util.PeakMemoryUsageMonitor;
import jloda.util.ProgramExecutorService;
import jloda.util.UsageException;

/**
 * SplitsTreeServer program
 * Daniel Huson, 11.2024
 */
public class SplitsTreeServer extends Application {
	public static final String Version = "SplitsTreeServer0.1";

	@Override
	public void start(Stage stage) throws Exception {
	}

	/**
	 * * main
	 */
	public static void main(String[] args) {
		try {
			Basic.startCollectionStdErr();
			ProgramProperties.setProgramName("SplitsTreeServer");
			ProgramProperties.setProgramVersion(splitstree6.main.Version.SHORT_DESCRIPTION);

			ProgramProperties.setProgramLicence("""
					Copyright (C) 2026. This program comes with ABSOLUTELY NO WARRANTY.
					This is free software, licensed under the terms of the GNU General Public License, Version 3.
					Sources available at: https://github.com/husonlab/splitstree6
					""");

			PeakMemoryUsageMonitor.start();
			(new SplitsTreeServer()).run(args);
			System.exit(0);
		} catch (Exception ex) {
			Basic.caught(ex);
			System.exit(1);
		}
	}

	/**
	 * run
	 */
	private void run(String[] args) throws Exception {
		final var options = new ArgsOptions(args, this, "SplitsTree server via HTTP");
		options.setVersion(ProgramProperties.getProgramVersion());
		options.setAuthors("Daniel H. Huson");
		options.setLicense(ProgramProperties.getProgramLicence());
		options.setVersion(ProgramProperties.getProgramVersion());

		options.comment("Server:");
		final var endpoint = options.getOption("-e", "endpoint", "Endpoint name", "splitstree");

		final var port = options.getOption("-p", "port", "Server port", 8001);

		options.comment(ArgsOptions.OTHER);

		final var backlog = options.getOption("-bl", "backlog", "Set the socket backlog", 100);
		final var pageTimeout = options.getOption("-pt", "pageTimeout", "Number of seconds to keep pending pages alive", 10000);
		final var readsPerPage = options.getOption("-rpp", "readsPerPage", "Number of reads per page to serve", 100);

		ProgramExecutorService.setNumberOfCoresToUse(options.getOption("-t", "threads", "Number of threads", 8));
		Basic.setDebugMode(options.getOption("-d", "debug", "Debug mode", false));

		options.done();

		System.err.println("WARNING: SplitsTreeServer is under development and is not intended for public use");


		if (endpoint.isEmpty())
			throw new UsageException("--endpoint: must have positive length");

		final var server = new HttpServerST(endpoint, port, backlog, readsPerPage, pageTimeout);

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.err.println("Stopping http server...");
			System.err.println(server.getAbout());
			server.stop();
			System.err.println("Total time:  " + PeakMemoryUsageMonitor.getSecondsSinceStartString());
			System.err.println("Peak memory: " + PeakMemoryUsageMonitor.getPeakUsageString());
		}));

		System.err.println("Starting SplitsTree server...");
		server.start();
		System.err.println(server.getAbout());

		System.err.println("Server address:");
		System.err.println("http://" + server.getAddress().getHostAddress() + ":" + server.getSocketAddress().getPort() + "/" + endpoint);
		System.err.println("http://" + server.getAddress().getHostName() + ":" + server.getSocketAddress().getPort() + "/" + endpoint);
		System.err.println("Help: http://" + server.getAddress().getHostAddress() + ":" + server.getSocketAddress().getPort() + "/" + endpoint + "/help");
		System.err.println();

		Thread.sleep(Long.MAX_VALUE);
	}
}
