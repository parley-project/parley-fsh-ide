package us.bringardner.fsh.ide;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;


import us.bringardner.fsh.ide.TreeViewer2.ShowType;
import us.bringardner.fsh.ide.core.SyntaxNode;

public class FshTreeViewPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	
	private JTextArea errorTextArea;
	private TreeViewer2 treeView;
	private final ButtonGroup buttonGroup = new ButtonGroup();
	private JRadioButton showTextRadioButton;
	private JRadioButton showTokenRadioButton;
	private JRadioButton showBothRadioButton;
	

	protected void appendToError(String msg) {
		System.out.println("append "+msg);
		StringBuilder buf = new StringBuilder();
		String tmp1 = msg;
		while(tmp1.length()>80) {
			buf.append(tmp1.substring(0, 80));
			buf.append("\n");
			tmp1 = tmp1.substring(80);
		}
		if( !tmp1.isEmpty()) {
			buf.append(tmp1.substring(0));
		}
		buf.append("\n");
		String tmp = errorTextArea.getText()+"\n"+buf;

		errorTextArea.setText(tmp.trim());


	}

	/**
	 * Create the panel.
	 */
	public FshTreeViewPanel()  {

		setLayout(new BorderLayout(0, 0));

		JScrollPane northScrollPane = new JScrollPane();
		add(northScrollPane, BorderLayout.NORTH);

		JPanel northPanel = new JPanel();
		northScrollPane.setViewportView(northPanel);
		
		showTextRadioButton = new JRadioButton("Show Text");
		showTextRadioButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionShowTextOrTokenChanged();
			}
		});
		showTextRadioButton.setSelected(true);
		buttonGroup.add(showTextRadioButton);
		northPanel.add(showTextRadioButton);
		
		showTokenRadioButton = new JRadioButton("Show Token");
		showTokenRadioButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionShowTextOrTokenChanged();
			}
		});
		buttonGroup.add(showTokenRadioButton);
		northPanel.add(showTokenRadioButton);
		
		showBothRadioButton = new JRadioButton("Show Both");
		showBothRadioButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionShowTextOrTokenChanged();
			}
		});
		buttonGroup.add(showBothRadioButton);
		northPanel.add(showBothRadioButton);

		JScrollPane southScrollPane = new JScrollPane();
		add(southScrollPane, BorderLayout.SOUTH);

		JPanel southPanel = new JPanel();
		southScrollPane.setViewportView(southPanel);

		JPanel centerPanel = new JPanel();
		add(centerPanel, BorderLayout.CENTER);
		centerPanel.setLayout(new BorderLayout(0, 0));

		JSlider slider = new JSlider();
		slider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				double val = slider.getValue()/100.0;
				treeView.setScale(1+val);
			}
		});
		centerPanel.add(slider, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane();
		centerPanel.add(scrollPane, BorderLayout.CENTER);
		JScrollPane scrollPane_1 = new JScrollPane();
		centerPanel.add(scrollPane_1, BorderLayout.SOUTH);

		errorTextArea = new JTextArea();
		scrollPane_1.setViewportView(errorTextArea);
		
		treeView = new TreeViewer2();
		scrollPane.setViewportView(treeView);
		
		JScrollPane scrollPane_2 = new JScrollPane();
		centerPanel.add(scrollPane_2, BorderLayout.WEST);
		
		


	}
	
	protected void actionShowTextOrTokenChanged() {
		if( showTextRadioButton.isSelected()) {
			treeView.setShowType(ShowType.Text);
		} else if( showTokenRadioButton.isSelected()) {
			treeView.setShowType(ShowType.Token);
		} else {
			treeView.setShowType(ShowType.Both);
		}
		if( treeView.getTreeLayout() != null ) {
			setTree(treeView.getTreeLayout().getRoot(), null);
		}
		
	}

	

	public void setTree(SyntaxNode tree,String errors) {
		treeView.setTree(tree);
		if( errors !=null) {
			errorTextArea.setText(errors);
		}
		
	}

	
}
