package us.bringardner.fsh.ide;

import us.bringardner.fsh.ide.core.Breakpoint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;


public class BreakpointBorderFrame extends JFrame {

	private static final long serialVersionUID = 1L;
	private JPanel contentPane;

	private Icon breakpointIcon = new ImageIcon(Toolkit.getDefaultToolkit().getImage(FshIDE.class.getResource("/img/breakpoint.png")));
	private JTextArea lineNumberTextArea;
	private JPanel iconPanel;
	private List<Breakpoint> breaks = new ArrayList<>();
	int fontHeight=0;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					BreakpointBorderFrame frame = new BreakpointBorderFrame();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the frame.
	 */
	public BreakpointBorderFrame() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 450, 300);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));

		setContentPane(contentPane);
		contentPane.setLayout(new BorderLayout(0, 0));

		JScrollPane scrollPane = new JScrollPane();
		contentPane.add(scrollPane, BorderLayout.CENTER);

		JTextArea textArea = new JTextArea();
		scrollPane.setViewportView(textArea);

		JSplitPane splitPane = new JSplitPane();
		splitPane.setEnabled(false);
		splitPane.setDividerSize(0);
		scrollPane.setRowHeaderView(splitPane);

		lineNumberTextArea = new JTextArea();
		lineNumberTextArea.setFocusable(false);
		lineNumberTextArea.setText("1\n2\n3\n4");
		splitPane.setRightComponent(lineNumberTextArea);

		
		iconPanel = new JPanel() {
			private static final long serialVersionUID = 1L;

			
			
			@Override
			public void paint(Graphics g) {
				g.setColor(Color.white);
				g.fillRect(0, 0, getWidth(), getHeight());
				Font f = getFont();
				FontMetrics fm = g.getFontMetrics(f);
				fontHeight = fm.getHeight();
				for(Breakpoint b : breaks) {
					breakpointIcon.paintIcon(iconPanel, g, 0, fontHeight*(b.getLine()-1));	
				}				
			}	
		};
		iconPanel.setFocusable(false);
		iconPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				
				Point p = e.getPoint();
				int line = (p.y/fontHeight)+1;
				int rem = -1;
				for(int idx=0; idx < breaks.size(); idx++) {
					Breakpoint b = breaks.get(idx);
					if(b.getLine()==line) {
						rem = idx;
						break;
					} 
				}	
				if( rem >=0 ) {
					breaks.remove(rem);
				} else {
					Breakpoint bp = new Breakpoint();
					bp.setLine(line);
					breaks.add(bp);
				}
				iconPanel.updateUI();
			}
		});
		iconPanel.setPreferredSize(new Dimension(18, 0));
		iconPanel.setBorder(null);
		splitPane.setLeftComponent(iconPanel);
		iconPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

		JButton btnNewButton = new JButton("");
		btnNewButton.setOpaque(false);
		btnNewButton.setIcon(new ImageIcon(BreakpointBorderFrame.class.getResource("/img/breakpoint.png")));
		//iconPanel.add(btnNewButton);
	}
}
