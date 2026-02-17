/*
 *  Highlighter.java Copyright (C) 2024 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.view.displaytext.highlighters;

import javafx.application.Platform;
import jloda.fx.util.RunAfterAWhile;
import jloda.util.Basic;
import jloda.util.StringUtils;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class Highlighter {
	public enum Type {Universal, Nexus, XML}

	private Type type;
	private IHighlighter highlighter;

	// one worker thread for highlighting
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		var t = new Thread(r, "syntax-highlighter");
		t.setDaemon(true);
		return t;
	});

	// used to cancel / ignore stale runs
	private final AtomicLong generation = new AtomicLong(0);

	public Highlighter(CodeArea codeArea) {
		highlighter = new UniversalHighlighter();
		type = Type.Universal;

		codeArea.textProperty().addListener((v, p, n) ->
				RunAfterAWhile.applyInFXThread(this, () -> {
					var len = codeArea.getLength();
					if (len >= 10_000_000) {
						// optional: clear styles or keep last styles
						return;
					}

					// detect type (debounced by RunAfterAWhile)
					var line = StringUtils.getFirstLine(codeArea.getText()).trim().toLowerCase();

					if (line.startsWith("#nexus"))
						setType(Type.Nexus);
					else if (line.startsWith("<?xml"))
						setType(Type.XML);
					else
						setType(Type.Universal);

					// style once changes to text have stopped (still debounced)
					if (len < 10_000_000) {
						scheduleHighlight(codeArea);
					}
				})
		);

		// initial highlight
		scheduleHighlight(codeArea);
	}

	private void scheduleHighlight(CodeArea codeArea) {
		final long myGen = generation.incrementAndGet();

		final String textSnapshot = codeArea.getText();
		final IHighlighter snapshotHighlighter = this.highlighter;

		executor.execute(() -> {
			StyleSpans<Collection<String>> spans;
			try {
				spans = snapshotHighlighter.computeHighlighting(textSnapshot);
			} catch (Throwable ex) {
				Basic.caught(ex);
				return;
			}

			// apply only if nothing newer has been scheduled
			if (generation.get() != myGen) return;

			Platform.runLater(() -> {
				// double-check still latest and text unchanged enough
				if (generation.get() != myGen) return;
				if (!Objects.equals(codeArea.getText(), textSnapshot)) return;

				codeArea.setStyleSpans(0, spans);
			});
		});
	}

	public void shutdown() {
		executor.shutdownNow();
	}

	public void setType(Type type) {
		if (type != this.type) {
			this.type = type;
			highlighter = switch (type) {
				case Nexus -> new NexusHighlighter();
				case XML -> new XMLHighlighter();
				case Universal -> new UniversalHighlighter();
			};
		}
	}

	public Type getType() {
		return type;
	}

	public IHighlighter getHighlighter() {
		return highlighter;
	}

	public interface IHighlighter {
		StyleSpans<Collection<String>> computeHighlighting(String text);
	}
}