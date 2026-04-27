/*
 * AppProfile.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.main;

/**
 * Static holder for the currently active {@link IAppProfile}.
 * Host applications call {@link #setProfile(IAppProfile)} once at startup,
 * before the main window opens. If no profile is set, a default profile
 * returning "SplitsTree6" is used.
 */
public class AppProfile {
	private static IAppProfile profile = new DefaultProfile();

	public static void setProfile(IAppProfile p) {
		profile = (p != null) ? p : new DefaultProfile();
	}

	public static IAppProfile getProfile() {
		return profile;
	}

	private static class DefaultProfile implements IAppProfile {
		@Override
		public String getName() {
			return "SplitsTree6";
		}
	}
}