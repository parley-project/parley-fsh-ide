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
package us.bringardner.fsh.studio;

import javax.swing.JPanel;

import us.bringardner.fsh.DebugController;
import java.awt.FlowLayout;
import javax.swing.JButton;
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
		resumeButton.setIcon(ToolbarIcons.large("resume"));
		resumeButton.setToolTipText("Resume");
		add(resumeButton);

		JButton suspendButton = new JButton("");
		suspendButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionSuspend();
			}
		});
		suspendButton.setForeground(Color.YELLOW);
		suspendButton.setIcon(ToolbarIcons.large("suspend"));
		suspendButton.setToolTipText("Suspend");
		add(suspendButton);
		// (no Terminate here: the toolbar's Stop ends the script)

		JButton stepIntoButton = new JButton("");
		stepIntoButton.setIcon(ToolbarIcons.large("step_into"));
		stepIntoButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionStepInto();
			}
		});
		stepIntoButton.setToolTipText("Step Into");
		add(stepIntoButton);

		JButton stepOverButton = new JButton("");
		stepOverButton.setIcon(ToolbarIcons.large("step_over"));
		stepOverButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionStepOver();
			}
		});
		stepOverButton.setToolTipText("Step Over");
		add(stepOverButton);

		JButton stepToReturnButton = new JButton("");
		stepToReturnButton.setIcon(ToolbarIcons.large("step_return"));
		stepToReturnButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionStepToReturn();
			}
		});
		stepToReturnButton.setToolTipText("Step Out");
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
