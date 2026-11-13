/**
*	Copyright 2026 Tony Bringardner
*
*	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
*	You may obtain a copy of the License at
*
*	http://www.apache.org/licenses/LICENSE-2.0
*
*	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
*	the specific language governing permissions and limitations under the License.
*/
package us.bringardner.fsh.studio;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import us.bringardner.fsh.studio.core.ScriptOutline;

/**
 * The script's outline beside the editor: its functions (with the variables each sets) and its
 * variables. A click on one (or Enter) goes to its line; a variable's right-click menu watches it.
 */
public class OutlinePanel extends JPanel {

	private static final long serialVersionUID = 1L;

	static final String FUNCTIONS = "Functions";
	static final String VARIABLES = "Variables";

	private final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
	private final DefaultTreeModel model = new DefaultTreeModel(root);
	private final JTree tree = new JTree(model);
	// what the user collapsed (groups by name, functions as name()), kept as the outline is rebuilt
	private final Set<String> collapsed = new HashSet<>();
	private boolean rebuilding;
	private IntConsumer onGoToLine = line->{};
	private java.util.function.Consumer<String> onWatchVariable = name->{};
	private ScriptOutline outline = ScriptOutline.of(null);
	private final DefaultTreeCellRenderer renderer;
	private final JScrollPane scroll;

	public OutlinePanel() {
		setLayout(new BorderLayout());
		JLabel title = new JLabel("Outline");
		title.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
		add(title, BorderLayout.NORTH);
		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		renderer = new DefaultTreeCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
				Object o = ((DefaultMutableTreeNode) value).getUserObject();
				String text;
				if( o instanceof ScriptOutline.Item ) {
					ScriptOutline.Item item = (ScriptOutline.Item) o;
					text = item.label()+"  : "+item.getLine();
				} else {
					text = String.valueOf(o);
				}
				super.getTreeCellRendererComponent(t, text, sel, expanded, leaf, row, hasFocus);
				// no folder and file icons: the names say what they are
				setIcon(null);
				return this;
			}
		};
		tree.setCellRenderer(renderer);
		tree.addTreeExpansionListener(new TreeExpansionListener() {
			@Override
			public void treeExpanded(TreeExpansionEvent e) {
				if( !rebuilding ) {
					collapsed.remove(key(e.getPath()));
				}
			}

			@Override
			public void treeCollapsed(TreeExpansionEvent e) {
				if( !rebuilding ) {
					collapsed.add(key(e.getPath()));
				}
			}
		});
		tree.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				TreePath path = tree.getPathForLocation(e.getX(), e.getY());
				if( path != null && !e.isPopupTrigger() && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
					goTo(path);
				}
			}

			@Override
			public void mousePressed(MouseEvent e) {
				showMenu(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				showMenu(e);
			}

			// right-click on a variable: watch it
			private void showMenu(MouseEvent e) {
				TreePath path = tree.getPathForLocation(e.getX(), e.getY());
				String name = path == null ? null : variableName(path);
				if( !e.isPopupTrigger() || name == null ) {
					return;
				}
				tree.setSelectionPath(path);
				javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
				javax.swing.JMenuItem watch = new javax.swing.JMenuItem("Watch "+name+"...");
				watch.addActionListener(a->onWatchVariable.accept(name));
				menu.add(watch);
				menu.show(tree, e.getX(), e.getY());
			}
		});
		tree.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if( e.getKeyCode() == KeyEvent.VK_ENTER && tree.getSelectionPath() != null ) {
					goTo(tree.getSelectionPath());
				}
			}
		});
		scroll = new JScrollPane(tree);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		add(scroll, BorderLayout.CENTER);
		setPreferredSize(new Dimension(200, 300));
		setMinimumSize(new Dimension(80, 50));
	}

	/** Called with a (1-based) line chosen in the outline. */
	public void setOnGoToLine(IntConsumer handler) {
		onGoToLine = handler == null ? line->{} : handler;
	}

	/** Called with a variable's name when Watch is chosen on its right-click menu. */
	public void setOnWatchVariable(java.util.function.Consumer<String> handler) {
		onWatchVariable = handler == null ? name->{} : handler;
	}

	/** The variable's name at path, or null for a function or a group. */
	static String variableName(TreePath path) {
		Object o = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		if( o instanceof ScriptOutline.Item && !((ScriptOutline.Item) o).isFunction()) {
			return ((ScriptOutline.Item) o).getName();
		}
		return null;
	}

	private void goTo(TreePath path) {
		Object o = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		if( o instanceof ScriptOutline.Item ) {
			onGoToLine.accept(((ScriptOutline.Item) o).getLine());
		}
	}

	private static String key(TreePath path) {
		Object o = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		return o instanceof ScriptOutline.Item ? ((ScriptOutline.Item) o).label() : String.valueOf(o);
	}

	/** Shows a script's outline (an empty one for none). */
	public void setOutline(ScriptOutline outline) {
		this.outline = outline == null ? ScriptOutline.of(null) : outline;
		rebuilding = true;
		try {
			root.removeAllChildren();
			group(FUNCTIONS, this.outline.getFunctions());
			group(VARIABLES, this.outline.getVariables());
			model.reload();
			// open, except what the user closed
			for(int row=0; row < tree.getRowCount(); row++ ) {
				TreePath path = tree.getPathForRow(row);
				if( !collapsed.contains(key(path))) {
					tree.expandPath(path);
				}
			}
		} finally {
			rebuilding = false;
		}
	}

	private void group(String name, List<ScriptOutline.Item> items) {
		if( items.isEmpty()) {
			return;
		}
		DefaultMutableTreeNode group = new DefaultMutableTreeNode(name);
		for(ScriptOutline.Item item : items) {
			DefaultMutableTreeNode node = new DefaultMutableTreeNode(item);
			for(ScriptOutline.Item v : item.getChildren()) {
				node.add(new DefaultMutableTreeNode(v));
			}
			group.add(node);
		}
		root.add(group);
	}

	// for tests
	ScriptOutline outline() { return outline; }
	JTree tree() { return tree; }
}
