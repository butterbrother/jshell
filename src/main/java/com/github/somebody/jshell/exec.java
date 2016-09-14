/*
 */
package com.github.somebody.jshell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import org.apache.commons.lang3.StringEscapeUtils;

/**
 * Exec output grabber.
 */
public class exec implements Runnable {

	private final PrintWriter writer;
	private final Process proc;
	private boolean done = false;

	public exec(PrintWriter writer, Process proc) {
		this.writer = writer;
		this.proc = proc;

		new Thread(this).start();
	}

	@Override
	public void run() {

		System.out.println("JShell: running");

		boolean cp866;
		try {
			cp866 = System.getProperty("os.name").toLowerCase().contains("wind");
		} catch (NullPointerException err) {
			cp866 = false;
		}
		Charset inputEnc = cp866 ? Charset.forName("CP866") : Charset.defaultCharset();

		try (BufferedReader stdout = new BufferedReader(new InputStreamReader(proc.getInputStream(), inputEnc));
			BufferedReader stderr = new BufferedReader(new InputStreamReader(proc.getErrorStream(), inputEnc))) {

			String bufferOut, bufferErr;
			do {
				bufferOut = stdout.readLine();
				bufferErr = stderr.readLine();

				if (bufferErr != null) {
					writer.print(StringEscapeUtils.escapeHtml4("<STDERR> "));

					writer.write(StringEscapeUtils.escapeHtml4(bufferErr));

					writer.println("<br>");

					System.out.println("STDERR: " + bufferErr);
				}

				if (bufferOut != null) {
					writer.print(StringEscapeUtils.escapeHtml4("<STDOUT> "));

					writer.print(StringEscapeUtils.escapeHtml4(bufferOut));

					writer.println("<br>");

					System.out.println("STDOUT: " + bufferOut);
				}
			} while (bufferOut != null || bufferErr != null);

		} catch (IOException err) {
			writer.println("I/O Error: " + err.getLocalizedMessage() + "<br>");
		}

		done = true;
	}

	public boolean isDone() {
		return done;
	}
}
