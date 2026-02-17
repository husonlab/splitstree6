/*
 * DebouncedHighlighter.java Copyright (C) 2026 Daniel H. Huson
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
 *
 */

package splitstree6.view.displaytext.highlighters;

import javafx.concurrent.Task;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DebouncedHighlighter {

	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		var t = new Thread(r, "syntax-highlighter");
		t.setDaemon(true);
		return t;
	});

	public void install(CodeArea codeArea, Highlighter.IHighlighter highlighter, Duration debounce) {
		// Debounce + async compute + cancel stale + apply latest
		codeArea.multiPlainChanges()
				.successionEnds(debounce)
				.supplyTask(() -> computeAsync(highlighter, codeArea.getText()))
				.awaitLatest(codeArea.multiPlainChanges())
				.filterMap(t -> {
					if (t.isSuccess()) return Optional.of(t.get());
					t.getFailure().printStackTrace();
					return Optional.empty();
				})
				.subscribe(spans -> codeArea.setStyleSpans(0, spans));

		// Initial highlight
		codeArea.setStyleSpans(0, highlighter.computeHighlighting(codeArea.getText()));
	}

	private Task<StyleSpans<Collection<String>>> computeAsync(Highlighter.IHighlighter highlighter, String text) {
		Task<StyleSpans<Collection<String>>> task = new Task<>() {
			@Override
			protected StyleSpans<Collection<String>> call() {
				return highlighter.computeHighlighting(text);
			}
		};
		executor.execute(task);
		return task;
	}

	public void shutdown() {
		executor.shutdownNow();
	}
}