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

import javax.swing.JPanel;

import us.bringardner.fsh.DebugController;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class DebugControlPanel extends JPanel {


	private static final long serialVersionUID = 1L;
	private DebugController controller;


	public DebugControlPanel(DebugController controller) {
		this();
		this.setController(controller);
	}

	/**
	 * Create the panel.
	 */
	public DebugControlPanel() {
		FlowLayout flowLayout = (FlowLayout) getLayout();
		flowLayout.setAlignment(FlowLayout.LEFT);

		JButton resumeButton = new JButton("");
		resumeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionResume();
			}
		});
		resumeButton.setIcon(new ImageIcon(DebugControlPanel.class.getResource("/img/resume.png")));
		resumeButton.setToolTipText("Resume");
		add(resumeButton);

		JButton suspendButton = new JButton("");
		suspendButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionSuspend();
			}
		});
		suspendButton.setForeground(Color.YELLOW);
		suspendButton.setIcon(new ImageIcon(DebugControlPanel.class.getResource("/img/suspend.png")));
		suspendButton.setToolTipText("Suspend");
		add(suspendButton);

		JButton terminateButton = new JButton("");
		terminateButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionTerminate();
			}
		});
		terminateButton.setIcon(new ImageIcon(DebugControlPanel.class.getResource("/img/terminate.png")));
		terminateButton.setToolTipText("Terminate");
		add(terminateButton);

		JButton stepIntoButton = new JButton("");
		stepIntoButton.setIcon(new ImageIcon(DebugControlPanel.class.getResource("/img/step_into.png")));
		stepIntoButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionStepInto();
			}
		});
		stepIntoButton.setToolTipText("Step Into");
		add(stepIntoButton);

		JButton stepOverButton = new JButton("");
		stepOverButton.setIcon(new ImageIcon(DebugControlPanel.class.getResource("/img/step_over.png")));
		stepOverButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionStepOver();
			}
		});
		stepOverButton.setToolTipText("Step Over");
		add(stepOverButton);

		JButton stepToReturnButton = new JButton("");
		stepToReturnButton.setIcon(new ImageIcon(DebugControlPanel.class.getResource("/img/step_return.png")));
		stepToReturnButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionStepToReturn();
			}
		});
		stepToReturnButton.setToolTipText("Step return");
		add(stepToReturnButton);

	}

	protected void actionStepToReturn() {
		if( controller != null ) {
			controller.stepToReturn();
		}

	}

	protected void actionStepOver() {
		if( controller != null ) {
			controller.stepOver();
		}

	}

	protected void actionStepInto() {
		if( controller != null ) {
			controller.stepInto();
		}

	}

	protected void actionTerminate() {
		if( controller != null ) {
			controller.terminate();
		}

	}

	protected void actionSuspend() {
		if( controller != null ) {
			controller.suspend();
		}

	}

	protected void actionResume() {
		if( controller != null ) {
			controller.resume();
		}

	}

	public DebugController getController() {
		return controller;
	}

	public void setController(DebugController controller) {
		this.controller = controller;
	}

}
