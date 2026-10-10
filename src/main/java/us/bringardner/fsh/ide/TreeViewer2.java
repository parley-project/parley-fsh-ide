/**
*	Copyright 2024 Tony Bringardner
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
package us.bringardner.fsh.ide;

/*
 * The drawing is based on ANTLR's TreeViewer:
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import javax.swing.JComponent;

import org.abego.treelayout.NodeExtentProvider;
import org.abego.treelayout.TreeForTreeLayout;
import org.abego.treelayout.TreeLayout;
import org.abego.treelayout.util.AbstractTreeForTreeLayout;
import org.abego.treelayout.util.DefaultConfiguration;

import us.bringardner.fsh.ide.core.SyntaxNode;

/**
 * Draws a script's syntax tree: a box for each node (its text, its kind, or both) with lines to
 * its children.
 */
public class TreeViewer2 extends JComponent {
	private static final long serialVersionUID = 1L;

	/** what a node's box shows: its text, its kind (if, word, command ...), or both */
	public enum ShowType {Both,Text,Token};

	/** how the layout walks the tree */
	private static class Adaptor extends AbstractTreeForTreeLayout<SyntaxNode> {
		Adaptor(SyntaxNode root) {
			super(root);
		}

		@Override
		public SyntaxNode getParent(SyntaxNode node) {
			return node.getParent();
		}

		@Override
		public List<SyntaxNode> getChildrenList(SyntaxNode node) {
			return node.getChildren();
		}
	}

	private static class ExtentProvider implements NodeExtentProvider<SyntaxNode> {
		private final TreeViewer2 viewer;

		ExtentProvider(TreeViewer2 viewer) {
			this.viewer = viewer;
		}

		@Override
		public double getWidth(SyntaxNode node) {
			FontMetrics fontMetrics = viewer.getFontMetrics(viewer.font);
			return fontMetrics.stringWidth(viewer.label(node)) + viewer.nodeWidthPadding*2;
		}

		@Override
		public double getHeight(SyntaxNode node) {
			FontMetrics fontMetrics = viewer.getFontMetrics(viewer.font);
			return fontMetrics.getHeight() + viewer.nodeHeightPadding*2;
		}
	}

	protected TreeLayout<SyntaxNode> treeLayout;
	protected List<SyntaxNode> highlightedNodes;

	protected String fontName = "Helvetica";
	protected int fontStyle = Font.PLAIN;
	protected int fontSize = 11;
	protected Font font = new Font(fontName, fontStyle, fontSize);

	protected double gapBetweenLevels = 17;
	protected double gapBetweenNodes = 20;
	protected int nodeWidthPadding = 12;
	protected int nodeHeightPadding = 0;
	protected int arcSize = 0;

	protected double scale = 1.0;

	protected Color boxColor = null;
	protected Color highlightedBoxColor = Color.lightGray;
	protected Color borderColor = null;
	protected Color textColor = Color.blue;
	protected ShowType showType = ShowType.Text;

	private boolean useCurvedEdges = false;

	public TreeViewer2() {
		setFont(font);
	}

	public TreeViewer2(SyntaxNode tree) {
		setFont(font);
		setTree(tree);
	}

	/** the text in a node's box, on one line */
	String label(SyntaxNode node) {
		String text = node.getText();
		String ret;
		switch (showType) {
		case Token: ret = node.getKind(); break;
		case Both: ret = text.isEmpty() ? node.getKind() : node.getKind()+": "+text; break;
		default: ret = text.isEmpty() ? node.getKind() : text; break;
		}
		ret = ret.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
		if( node.getLine() > 0 ) {
			ret += " ("+node.getLine()+")";
		}
		return ret;
	}

	public List<SyntaxNode> getHighlightedNodes() {
		return highlightedNodes;
	}

	public void setHighlightedNodes(List<SyntaxNode> highlightedNodes) {
		this.highlightedNodes = highlightedNodes;
	}

	public double getGapBetweenLevels() {
		return gapBetweenLevels;
	}

	public void setGapBetweenLevels(double gapBetweenLevels) {
		this.gapBetweenLevels = gapBetweenLevels;
	}

	public double getGapBetweenNodes() {
		return gapBetweenNodes;
	}

	public void setGapBetweenNodes(double gapBetweenNodes) {
		this.gapBetweenNodes = gapBetweenNodes;
	}

	public ShowType getShowType() {
		return showType;
	}

	public void setShowType(ShowType showType) {
		this.showType = showType;
	}

	public boolean getUseCurvedEdges() {
		return useCurvedEdges;
	}

	public void setUseCurvedEdges(boolean useCurvedEdges) {
		this.useCurvedEdges = useCurvedEdges;
	}

	private void updatePreferredSize() {
		setPreferredSize(getScaledTreeSize());
		invalidate();
		if (getParent() != null) {
			getParent().validate();
		}
		repaint();
	}

	// ---------------- PAINT -----------------------------------------------

	protected void paintEdges(Graphics g, SyntaxNode parent) {
		if (!getTreeLayout().isLeaf(parent)) {
			((Graphics2D)g).setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			Rectangle2D.Double parentBounds = getBoundsOfNode(parent);
			double x1 = parentBounds.getCenterX();
			double y1 = parentBounds.getMaxY();
			for (SyntaxNode child : getTreeLayout().getChildren(parent)) {
				Rectangle2D.Double childBounds = getBoundsOfNode(child);
				double x2 = childBounds.getCenterX();
				double y2 = childBounds.getMinY();
				if (getUseCurvedEdges()) {
					CubicCurve2D c = new CubicCurve2D.Double();
					c.setCurve(x1, y1, x1, (y1+y2)/2, x2, y1, x2, y2);
					((Graphics2D) g).draw(c);
				} else {
					g.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
				}
				paintEdges(g, child);
			}
		}
	}

	protected void paintBox(Graphics g, SyntaxNode node) {
		Rectangle2D.Double box = getBoundsOfNode(node);
		if ( isHighlighted(node) || boxColor!=null ) {
			g.setColor(isHighlighted(node) ? highlightedBoxColor : boxColor);
			g.fillRoundRect((int) box.x, (int) box.y, (int) box.width - 1, (int) box.height - 1, arcSize, arcSize);
		}
		if ( borderColor!=null ) {
			g.setColor(borderColor);
			g.drawRoundRect((int) box.x, (int) box.y, (int) box.width - 1, (int) box.height - 1, arcSize, arcSize);
		}
		g.setColor(textColor);
		FontMetrics m = getFontMetrics(font);
		int x = (int) box.x + arcSize / 2 + nodeWidthPadding;
		int y = (int) box.y + m.getAscent() + m.getLeading() + 1 + nodeHeightPadding;
		g.drawString(label(node), x, y);
	}

	@Override
	public void paint(Graphics g) {
		super.paint(g);
		if ( treeLayout==null ) {
			return;
		}
		Graphics2D g2 = (Graphics2D)g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		paintEdges(g, getTreeLayout().getRoot());
		for (SyntaxNode node : treeLayout.getNodeBounds().keySet()) {
			paintBox(g, node);
		}
	}

	@Override
	protected Graphics getComponentGraphics(Graphics g) {
		Graphics2D g2d=(Graphics2D)g;
		g2d.scale(scale, scale);
		return super.getComponentGraphics(g2d);
	}

	private Dimension getScaledTreeSize() {
		Dimension size = treeLayout.getBounds().getBounds().getSize();
		return new Dimension(50+(int)(size.width*scale), (int)(size.height*scale));
	}

	private Rectangle2D.Double getBoundsOfNode(SyntaxNode node) {
		return treeLayout.getNodeBounds().get(node);
	}

	public void setFontSize(int sz) {
		fontSize = sz;
		font = new Font(fontName, fontStyle, fontSize);
	}

	public void setFontName(String name) {
		fontName = name;
		font = new Font(fontName, fontStyle, fontSize);
	}

	private boolean isHighlighted(SyntaxNode node) {
		if ( highlightedNodes==null ) {
			return false;
		}
		for(SyntaxNode n : highlightedNodes) {
			if( n == node ) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Font getFont() {
		return font;
	}

	@Override
	public void setFont(Font font) {
		this.font = font;
	}

	public void setBoxColor(Color boxColor) {
		this.boxColor = boxColor;
	}

	public void setBorderColor(Color borderColor) {
		this.borderColor = borderColor;
	}

	public void setTextColor(Color textColor) {
		this.textColor = textColor;
	}

	/** the tree shown, or null */
	public TreeForTreeLayout<SyntaxNode> getTreeLayout() {
		return treeLayout == null ? null : treeLayout.getTree();
	}

	public void setTree(SyntaxNode root) {
		if ( root!=null ) {
			treeLayout = new TreeLayout<SyntaxNode>(new Adaptor(root), new ExtentProvider(this),
					new DefaultConfiguration<SyntaxNode>(gapBetweenLevels, gapBetweenNodes), true);
			updatePreferredSize();
		} else {
			treeLayout = null;
			repaint();
		}
	}

	public double getScale() {
		return scale;
	}

	public void setScale(double scale) {
		this.scale = scale <= 0 ? 1 : scale;
		if( treeLayout != null ) {
			updatePreferredSize();
		}
	}
}
