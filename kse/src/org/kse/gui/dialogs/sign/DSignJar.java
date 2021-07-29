/*
 * Copyright 2004 - 2013 Wayne Grant
 *           2013 - 2021 Kai Kramer
 *
 * This file is part of KeyStore Explorer.
 *
 * KeyStore Explorer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * KeyStore Explorer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with KeyStore Explorer.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.kse.gui.dialogs.sign;

import java.awt.Container;
import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;
import java.util.jar.JarFile;

import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.kse.KSE;
import org.kse.crypto.CryptoException;
import org.kse.crypto.digest.DigestType;
import org.kse.crypto.keypair.KeyPairType;
import org.kse.crypto.signing.JarSigner;
import org.kse.crypto.signing.SignatureType;
import org.kse.crypto.x509.X500NameUtils;
import org.kse.crypto.x509.X509CertificateGenerator;
import org.kse.crypto.x509.X509CertificateVersion;
import org.kse.gui.CurrentDirectory;
import org.kse.gui.CursorUtil;
import org.kse.gui.FileChooserFactory;
import org.kse.gui.JEscDialog;
import org.kse.gui.JavaFXFileChooser;
import org.kse.gui.MiGUtil;
import org.kse.gui.PlatformUtil;
import org.kse.gui.dialogs.DialogHelper;
import org.kse.gui.error.DError;
import org.kse.gui.error.DProblem;
import org.kse.gui.error.Problem;
import org.kse.utilities.DialogViewer;
import org.kse.utilities.io.FileNameUtil;
import org.kse.utilities.io.IOUtils;
import org.kse.utilities.net.URLs;

import net.miginfocom.swing.MigLayout;

/**
 * Dialog that displays the presents JAR signing options.
 *
 */
public class DSignJar extends JEscDialog {
	private static final long serialVersionUID = -5095469699284737624L;

	private static ResourceBundle res = ResourceBundle.getBundle("org/kse/gui/dialogs/sign/resources");

	private static final String CANCEL_KEY = "CANCEL_KEY";

	private JLabel jlOutputJarFixes;
	private JRadioButton jrbOutputJarFixes;
	private JLabel jlSignDirectly;
	private JRadioButton jrbSignDirectly;

	private JLabel jlInputJar;
	private JTextField jtfInputJar;
	private JButton jbInputJarBrowse;
	private JLabel jlInputJarFolder;
	private JTextField jtfInputJarFolder;
	private JButton jbInputJarFolderBrowse;
	private JLabel jlPrefix;
	private JTextField jtfPrefix;
	private JLabel jlSuffix;
	private JTextField jtfSuffix;
	private JLabel jlSignatureName;
	private JTextField jtfSignatureName;
	private JLabel jlSignatureAlgorithm;
	private JComboBox<SignatureType> jcbSignatureAlgorithm;
	private JLabel jlDigestAlgorithm;
	private JComboBox<DigestType> jcbDigestAlgorithm;
	private JLabel jlAddTimestamp;
	private JCheckBox jcbAddTimestamp;
	private JLabel jlTimestampServerUrl;
	private JComboBox<String> jcbTimestampServerUrl;
	private JPanel jpButtons;
	private JButton jbOK;
	private JButton jbCancel;

	private PrivateKey signPrivateKey;
	private KeyPairType signKeyPairType;
	private File[] inputJarFiles;
	private List<File> outputJarFiles;
	private String signatureName;
	private SignatureType signatureType;
	private DigestType digestType;
	private String tsaUrl;
	private X509Certificate[] kseCerts;
	private Provider kseProvider;
	private String kseSigner;
	private boolean successStatus = true;

	/**
	 * Creates a new DSignJar dialog.
	 *
	 * @param parent          The parent frame
	 * @param signPrivateKey  Signing key pair's private key
	 * @param signKeyPairType Signing key pair's type
	 * @param signatureName   Default signature name
	 * @throws CryptoException A crypto problem was encountered constructing the
	 *                         dialog
	 */
	public DSignJar(JFrame parent, PrivateKey signPrivateKey, KeyPairType signKeyPairType, String signatureName,
			X509Certificate[] certs, Provider provider, String signer) throws CryptoException {
		super(parent, Dialog.ModalityType.DOCUMENT_MODAL);
		this.signPrivateKey = signPrivateKey;
		this.signKeyPairType = signKeyPairType;
		this.kseCerts = certs;
		this.kseProvider = provider;
		this.kseSigner = signer;
		setTitle(res.getString("DSignJar.Title"));
		initComponents(signatureName);
	}

	/**
	 * Initializes the dialogue panel and associated elements
	 * 
	 * @param signatureName String
	 * @throws CryptoException
	 */
	private void initComponents(String signatureName) throws CryptoException {

		jlSignDirectly = new JLabel(res.getString("DSignJar.jlSignDirectly.text"));
		jrbSignDirectly = new JRadioButton("", true);
		jrbSignDirectly.setToolTipText(res.getString("DSignJar.jrbSignDirectly.tooltip"));

		jlOutputJarFixes = new JLabel(res.getString("DSignJar.jlOutputJarFixes.text"));
		jrbOutputJarFixes = new JRadioButton("", false);

		ButtonGroup buttonGroup = new ButtonGroup();
		buttonGroup.add(jrbSignDirectly);
		buttonGroup.add(jrbOutputJarFixes);

		jlInputJar = new JLabel(res.getString("DSignJar.jlInputJar.text"));
		jtfInputJar = new JTextField(30);
		jtfInputJar.setCaretPosition(0);
		jtfInputJar.setToolTipText(res.getString("DSignJar.jtfInputJar.tooltip"));

		jbInputJarBrowse = new JButton(res.getString("DSignJar.jbInputJarBrowse.text"));
		PlatformUtil.setMnemonic(jbInputJarBrowse, res.getString("DSignJar.jbInputJarBrowse.mnemonic").charAt(0));
		jbInputJarBrowse.setToolTipText(res.getString("DSignJar.jbInputJarBrowse.tooltip"));

		jlInputJarFolder = new JLabel(res.getString("DSignJar.jlInputJarFolder.text"));
		jtfInputJarFolder = new JTextField(30);
		jtfInputJarFolder.setEnabled(false);
		jtfInputJarFolder.setCaretPosition(0);
		jtfInputJarFolder.setToolTipText(res.getString("DSignJar.jtfInputJarFolder.tooltip"));

		jbInputJarFolderBrowse = new JButton(res.getString("DSignJar.jbInputJarFolderBrowse.text"));
		PlatformUtil.setMnemonic(jbInputJarFolderBrowse,
				res.getString("DSignJar.jbInputJarFolderBrowse.mnemonic").charAt(0));
		jbInputJarFolderBrowse.setToolTipText(res.getString("DSignJar.jbInputJarFolderBrowse.tooltip"));
		jbInputJarFolderBrowse.setEnabled(true);

		jlPrefix = new JLabel(res.getString("DSignJar.jlPrefix.text"));
		jtfPrefix = new JTextField("", 15);
		jtfPrefix.setEnabled(false);
		jtfPrefix.setCaretPosition(0);
		jtfPrefix.setToolTipText(res.getString("DSignJar.jtfPrefix.tooltip"));

		jlSuffix = new JLabel(res.getString("DSignJar.jlSuffix.text"));
		jtfSuffix = new JTextField("", 15);
		jtfSuffix.setEnabled(false);
		jtfSuffix.setCaretPosition(0);
		jtfSuffix.setToolTipText(res.getString("DSignJar.jtfSuffix.tooltip"));

		jlSignatureName = new JLabel(res.getString("DSignJar.jlSignatureName.text"));
		jtfSignatureName = new JTextField(convertSignatureName(signatureName), 15);
		jtfSignatureName.setCaretPosition(0);
		jtfSignatureName.setToolTipText(res.getString("DSignJar.jtfSignatureName.tooltip"));

		jlSignatureAlgorithm = new JLabel(res.getString("DSignJar.jlSignatureAlgorithm.text"));
		jcbSignatureAlgorithm = new JComboBox<>();
		DialogHelper.populateSigAlgs(signKeyPairType, this.signPrivateKey, jcbSignatureAlgorithm);
		jcbSignatureAlgorithm.setToolTipText(res.getString("DSignJar.jcbSignatureAlgorithm.tooltip"));

		jlDigestAlgorithm = new JLabel(res.getString("DSignJar.jlDigestAlgorithm.text"));
		jcbDigestAlgorithm = new JComboBox<>();
		populateDigestAlgs();
		jcbDigestAlgorithm.setToolTipText(res.getString("DSignJar.jcbDigestAlgorithm.tooltip"));

		jlAddTimestamp = new JLabel(res.getString("DSignJar.jlAddTimestamp.text"));
		jcbAddTimestamp = new JCheckBox();
		jcbAddTimestamp.setSelected(false);
		jcbAddTimestamp.setToolTipText(res.getString("DSignJar.jcbAddTimestamp.tooltip"));

		jlTimestampServerUrl = new JLabel(res.getString("DSignJar.jlTimestampServerUrl.text"));
		jcbTimestampServerUrl = new JComboBox<>();
		jcbTimestampServerUrl.setEditable(true);
		jcbTimestampServerUrl.setEnabled(false);
		jcbTimestampServerUrl.setToolTipText(res.getString("DSignJar.jcbTimestampServerUrl.tooltip"));
		jcbTimestampServerUrl.setModel(new DefaultComboBoxModel<>(URLs.TSA_URLS));

		jbOK = new JButton(res.getString("DSignJar.jbOK.text"));

		jbCancel = new JButton(res.getString("DSignJar.jbCancel.text"));
		jbCancel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
				CANCEL_KEY);

		jpButtons = PlatformUtil.createDialogButtonPanel(jbOK, jbCancel);

		// layout
		Container pane = getContentPane();
		pane.setLayout(new MigLayout("insets dialog, fill", "[para]unrel[right]unrel[]", "[]unrel[]"));
		MiGUtil.addSeparator(pane, res.getString("DSignJar.jlFiles.text"));
		pane.add(jlInputJarFolder, "skip");
		pane.add(jtfInputJarFolder, "sgx");
		pane.add(jbInputJarFolderBrowse, "wrap");
		pane.add(jlInputJar, "skip");
		pane.add(jbInputJarBrowse, "wrap");
		pane.add(jlSignDirectly, "skip");
		pane.add(jrbSignDirectly, "wrap");
		pane.add(jlOutputJarFixes, "skip");
		pane.add(jrbOutputJarFixes, "wrap");
		pane.add(jlPrefix, "skip");
		pane.add(jtfPrefix, "wrap");
		pane.add(jlSuffix, "skip");
		pane.add(jtfSuffix, "wrap");
		MiGUtil.addSeparator(pane, res.getString("DSignJar.jlSignature.text"));
		pane.add(jlSignatureName, "skip");
		pane.add(jtfSignatureName, "sgx, wrap");
		pane.add(jlSignatureAlgorithm, "skip");
		pane.add(jcbSignatureAlgorithm, "sgx, wrap");
		pane.add(jlDigestAlgorithm, "skip");
		pane.add(jcbDigestAlgorithm, "sgx, wrap para");
		MiGUtil.addSeparator(pane, res.getString("DSignJar.jlTimestamp.text"));
		pane.add(jlAddTimestamp, "skip");
		pane.add(jcbAddTimestamp, "wrap");
		pane.add(jlTimestampServerUrl, "skip");
		pane.add(jcbTimestampServerUrl, "sgx, wrap para");
		pane.add(new JSeparator(), "spanx, growx, wrap para");
		pane.add(jpButtons, "right, spanx");

		// actions
		jbInputJarBrowse.addActionListener(evt -> {
			try {
				CursorUtil.setCursorBusy(DSignJar.this);
				inputJarBrowsePressed();
			} finally {
				CursorUtil.setCursorFree(DSignJar.this);
			}
		});

		jbInputJarFolderBrowse.addActionListener(evt -> {
			try {
				CursorUtil.setCursorBusy(DSignJar.this);
				inputJarFolderBrowsePressed();
			} finally {
				CursorUtil.setCursorFree(DSignJar.this);
			}
		});

		jcbAddTimestamp.addItemListener(evt -> enableDisableElements());
		jrbSignDirectly.addItemListener(evt -> enableDisableElements());
		jrbOutputJarFixes.addItemListener(evt -> enableDisableElements());

		jbOK.addActionListener(evt -> okPressed());

		jbCancel.getActionMap().put(CANCEL_KEY, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent evt) {
				cancelPressed();
			}
		});
		jbCancel.addActionListener(evt -> cancelPressed());

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent evt) {
				closeDialog();
			}
		});

		setResizable(false);

		getRootPane().setDefaultButton(jbOK);

		pack();
		setLocationRelativeTo(null);

	}

	/**
	 * This function enables and disables elements in the dialog
	 */
	protected void enableDisableElements() {
		jtfPrefix.setEnabled(jrbOutputJarFixes.isSelected());
		jtfSuffix.setEnabled(jrbOutputJarFixes.isSelected());
		jcbTimestampServerUrl.setEnabled(jcbAddTimestamp.isSelected());
	}

	/**
	 * Convert the supplied signature name to make it valid for use with signing,
	 * i.e. any characters that are not 'a-z', 'A-Z', '0-9', '_' or '-' are
	 * converted to '_'
	 * 
	 * @param signatureName String
	 * @return String
	 */
	private String convertSignatureName(String signatureName) {
		StringBuilder sb = new StringBuilder(signatureName.length());

		for (int i = 0; i < signatureName.length(); i++) {
			char c = signatureName.charAt(i);

			if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z') && (c < '0' || c > '9') && c != '-' && c != '_') {
				c = '_';
			}
			sb.append(c);
		}

		return sb.toString();
	}

	/**
	 * Populate combination box with items
	 */
	private void populateDigestAlgs() {
		jcbDigestAlgorithm.removeAllItems();

		jcbDigestAlgorithm.addItem(DigestType.SHA1);
		jcbDigestAlgorithm.addItem(DigestType.SHA224);
		jcbDigestAlgorithm.addItem(DigestType.SHA256);
		jcbDigestAlgorithm.addItem(DigestType.SHA384);
		jcbDigestAlgorithm.addItem(DigestType.SHA512);

		jcbDigestAlgorithm.setSelectedItem(DigestType.SHA256);
	}

	/**
	 * Get chosen input JAR file.
	 *
	 * @return Input JAR file
	 */
	public File[] getInputJar() {
		return inputJarFiles;
	}

	/**
	 * Get chosen output JAR file.
	 *
	 * @return Output JAR file
	 */
	public List<File> getOutputJar() {
		return outputJarFiles;
	}

	/**
	 * Get chosen signature name.
	 *
	 * @return String Signature name or null if dialog cancelled
	 */
	public String getSignatureName() {
		return signatureName;
	}

	/**
	 * Get chosen signature type.
	 *
	 * @return SignatureType or null if dialog cancelled
	 */
	public SignatureType getSignatureType() {
		return signatureType;
	}

	/**
	 * Get chosen digest type.
	 *
	 * @return DigestType or null if dialog cancelled
	 */
	public DigestType getDigestType() {
		return digestType;
	}

	/**
	 * Get chosen TSA URL.
	 *
	 * @return String TSA URL or null if dialog cancelled
	 */
	public String getTimestampingServerUrl() {
		return tsaUrl;
	}

	/**
	 * Verify that the supplied signature name is valid for use in the signing of a
	 * JAR file, ie: contains only alphanumeric characters and the characters '-' or
	 * '_'
	 * 
	 * @param signatureName String
	 * @return Boolean
	 */
	private boolean verifySignatureName(String signatureName) {
		for (int i = 0; i < signatureName.length(); i++) {
			char c = signatureName.charAt(i);

			if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z') && (c < '0' || c > '9') && c != '-' && c != '_') {
				return false;
			}
		}

		return true;
	}

	/**
	 * The function checks the following - fields to ensure they are not empty -
	 * check the output file paths for overwriting files - check the signature for
	 * overwriting the same signature, - and initiate JAR signature.
	 */
	private void okPressed() {
		String signatureName = jtfSignatureName.getText().trim();
		String outputJarPrefix = jtfPrefix.getText().trim();
		String outputJarSuffix = jtfSuffix.getText().trim();

		// check if any files selected
		if ((inputJarFiles == null) || (inputJarFiles.length == 0)) {
			JOptionPane.showMessageDialog(this, res.getString("DSignJar.InputJarRequired.message"), getTitle(),
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		// check if signature field was filled
		if (signatureName.length() == 0) {
			JOptionPane.showMessageDialog(this, res.getString("DSignJar.ValReqSignatureName.message"), getTitle(),
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		// check if signature is verified
		if (!verifySignatureName(signatureName)) {
			JOptionPane.showMessageDialog(this, res.getString("DSignJar.ValJarSignatureName.message"), getTitle(),
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		// check if time stamp URL is empty
		if (jcbAddTimestamp.isSelected() && jcbTimestampServerUrl.getSelectedItem().toString().isEmpty()) {
			JOptionPane.showMessageDialog(this, res.getString("DSignJar.EmptyTimestampUrl.message"), getTitle(),
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		// checks if a file prefix or suffix fields were filled
		if(jrbOutputJarFixes.isSelected()) {
			if ((outputJarPrefix.length() == 0) && (outputJarSuffix.length() == 0)) {
				JOptionPane.showMessageDialog(this, res.getString("DSignJar.OutputJarRequired.message"), getTitle(),
						JOptionPane.WARNING_MESSAGE);
				return;
			}
		}

		this.signatureName = signatureName;
		signatureType = (SignatureType) jcbSignatureAlgorithm.getSelectedItem();
		digestType = (DigestType) jcbDigestAlgorithm.getSelectedItem();

		// check if add time stamp is selected and assign value
		if (jcbAddTimestamp.isSelected()) {
			tsaUrl = jcbTimestampServerUrl.getSelectedItem().toString();
		}

		// set output Jar files and check if the No Overwrite File option was selected
		if (!setOutputJarFiles()) {
			return;
		}

		// check signature and if the No Overwrite Signature option was selected
		if (!checkSignature()) {
			return;
		}

		//signJar();

		closeDialog();
	}

	/**
	 * Set output JAR files and check files for overwrite
	 * 
	 * @return Boolean
	 */
	private boolean setOutputJarFiles() {
		// loop input files array and set output files array
		String outputJarPrefix = jtfPrefix.getText().trim();
		String outputJarSuffix = jtfSuffix.getText().trim();
		final String FILE_SUFFIX = ".jar";
		JCheckBox checkbox = new JCheckBox(res.getString("DSignJar.OverwriteSkip.message"));
		
		// set input files array to output files list
		this.outputJarFiles = new ArrayList<File>(Arrays.asList(inputJarFiles));

		if (jrbOutputJarFixes.isSelected()) {
			// loop through output JAR files
			for (int i = 0; i < outputJarFiles.size(); i++) {
				// set prefix and suffix to the file name
				String fileBaseName = FileNameUtil.removeExtension(outputJarFiles.get(i).getName());
				String outFileName = outputJarFiles.get(i).getParent() + "\\" + outputJarPrefix + fileBaseName
						+ outputJarSuffix + FILE_SUFFIX;
				// replace file object in arraylist
				this.outputJarFiles.set(i, new File(outFileName));

				if (!checkbox.isSelected()) {
					// check if file exists
					if (outputJarFiles.get(i).isFile()) {
						String message = MessageFormat.format(res.getString("DSignJar.OverWriteOutputJarFile.message"),
								outputJarFiles.get(i));
						Object[] params = { message, checkbox };

						// check if overwrite is allowed and if checkbox is checked
						int selected = JOptionPane.showConfirmDialog(this, params, getTitle(),
								JOptionPane.YES_NO_OPTION);
						if (selected != JOptionPane.YES_OPTION) {
							this.outputJarFiles.clear();
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	/**
	 * Checks to overwrite an existing signature
	 * 
	 * @return Boolean
	 */
	private boolean checkSignature() {
		JCheckBox checkbox = new JCheckBox(res.getString("DSignJar.OverwriteSkip.message"));
		String message = MessageFormat.format(res.getString("DSignJar.SignatureOverwrite.message"), this.signatureName);
		Object[] params = { message, checkbox };

		for (int i = 0; i < inputJarFiles.length; i++) {
			try {
				if (JarSigner.hasSignature(inputJarFiles[i], this.signatureName)) {
					int selected = JOptionPane.showConfirmDialog(this, params, getTitle(), JOptionPane.YES_NO_OPTION);
					if (selected != JOptionPane.YES_OPTION) {
						return false;
					}
				}
			} catch (IOException ex) {
				DError.displayError(this, ex);
				return false;
			}
			// check to skip overwrite alert message
			if (checkbox.isSelected()) {
				return true;
			}
		}
		return true;
	}

	/**
	 * Get input JAR files
	 */
	private void inputJarBrowsePressed() {
		JFileChooser chooser = FileChooserFactory.getArchiveFileChooser();
		chooser.setCurrentDirectory(CurrentDirectory.get());
		chooser.setDialogTitle(res.getString("DSignJar.ChooseInputJar.Title"));
		chooser.setMultiSelectionEnabled(true);
		chooser.setApproveButtonText(res.getString("DSignJar.InputJarChooser.button"));

		int rtnValue = chooser.showOpenDialog(this);
		if (rtnValue == JFileChooser.APPROVE_OPTION) {
			File[] chosenFiles = chooser.getSelectedFiles();

			// check if selected are JAR files
			for (File file : chosenFiles) {
				if (!validJAR(file)) {
					return;
				}
			}
			
			// remove folder text field content to better indicate the input file was selected
			jtfInputJarFolder.setText("");
			
			CurrentDirectory.updateForFile(chosenFiles[0]);
			
			// set input files from file selector
			this.inputJarFiles = chooser.getSelectedFiles();
		}
	}

	/**
	 * Check if a file is a valid JAR
	 * 
	 * @param File accepts a jar file
	 * @return <tt>Boolean</tt> true if the file is a valid jar and false if not
	 */
	private boolean validJAR(File file) {
		JarFile jarFile = null;

		try {
			jarFile = new JarFile(file);
		} catch (IOException ex) {
			String problemStr = MessageFormat.format(res.getString("DSignJar.NoOpenJar.Problem"), file.getName());

			String[] causes = new String[] { res.getString("DSignJar.NotJar.Cause"),
					res.getString("DSignJar.CorruptedJar.Cause") };

			Problem problem = new Problem(problemStr, causes, ex);

			DProblem dProblem = new DProblem(this, res.getString("DSignJar.ProblemOpeningJar.Title"), problem);
			dProblem.setLocationRelativeTo(this);
			dProblem.setVisible(true);

			return false;
		} finally {
			IOUtils.closeQuietly(jarFile);
		}

		return true;
	}

	/**
	 * Sign selected JARs
	 */
	private void signJar() {
		for (int i = 0; i < inputJarFiles.length; i++) {
			try {
				if (inputJarFiles[i].equals(outputJarFiles.get(i))) {
					JarSigner.sign(inputJarFiles[i], signPrivateKey, kseCerts, signatureType, signatureName, kseSigner,
							digestType, tsaUrl, kseProvider);
				} else {
					JarSigner.sign(inputJarFiles[i], outputJarFiles.get(i), signPrivateKey, kseCerts, signatureType,
							signatureName, kseSigner, digestType, tsaUrl, kseProvider);
				}
			} catch (Exception ex) {
				DError.displayError(this, ex);
			}
		}
	}

	/**
	 * Get input JAR folder and JAR files
	 */
	private void inputJarFolderBrowsePressed() {
		JFileChooser chooser = FileChooserFactory.getArchiveFileChooser();
		chooser.setFileSelectionMode(JavaFXFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		final String FILE_SUFFIX = ".jar";

		// File currentFile = new File(jtfOutputJar.getText());
		chooser.setCurrentDirectory(CurrentDirectory.get());

		chooser.setDialogTitle(res.getString("DSignJar.ChooseInputJarFolder.Title"));
		chooser.setMultiSelectionEnabled(false);

		int rtnValue = JavaFXFileChooser.isFxAvailable() ? chooser.showDialog(this, "")
				: chooser.showDialog(this, res.getString("DSignJar.InputJarChooser.button"));
		if (rtnValue == JFileChooser.APPROVE_OPTION) {
			File chosenFolder = chooser.getSelectedFile();
			CurrentDirectory.update(chosenFolder);
			jtfInputJarFolder.setText(chosenFolder.toString());
			jtfInputJarFolder.setCaretPosition(0);

			// set input files from filtered file list of the folder selection
			this.inputJarFiles = chosenFolder.listFiles(new FilenameFilter() {
				public boolean accept(File chosenFolder, String filename) {
					return filename.endsWith(FILE_SUFFIX);
				}
			});
		}
	}
	
	
	/**
	 * Returns the current success status
	 *
	 * @return successStatus The success status boolean
	 */
	public boolean isSuccessful() {
		return successStatus;
	}


	/**
	 * Call the close dialog method
	 */
	private void cancelPressed() {
		successStatus = false;
		closeDialog();
	}

	/**
	 * Close the dialog method
	 */
	private void closeDialog() {
		setVisible(false);
		dispose();
	}

	// for quick UI testing
	public static void main(String[] args) throws Exception {
		DialogViewer.prepare();
		KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyPairType.RSA.jce(), "BC");
		kpg.initialize(1024, new SecureRandom());
		KeyPair kp = kpg.generateKeyPair();
		Provider provider = new BouncyCastleProvider();
		String signer = KSE.getFullApplicationName();
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");

		KeyPair caKeyPair = keyGen.genKeyPair();
		X509CertificateGenerator certGen = new X509CertificateGenerator(X509CertificateVersion.VERSION3);
		X509Certificate caCert = certGen.generateSelfSigned(new X500Name("cn=CA"), Date.from(Instant.now()),
				Date.from(Instant.now().plus(3650, ChronoUnit.DAYS)), caKeyPair.getPublic(), caKeyPair.getPrivate(),
				SignatureType.SHA224WITHRSAANDMGF1,
				new BigInteger(Hex.decode("1122334455667788990011223344556677889900")));

		KeyPair eeKeyPair = keyGen.genKeyPair();
		X509Certificate eeCert = certGen.generate(new X500Name("cn=EE"),
				X500NameUtils.x500PrincipalToX500Name(caCert.getSubjectX500Principal()), Date.from(Instant.now()),
				Date.from(Instant.now().plus(365, ChronoUnit.DAYS)), eeKeyPair.getPublic(), eeKeyPair.getPrivate(),
				SignatureType.SHA224WITHRSAANDMGF1,
				new BigInteger(Hex.decode("0011223344556677889900112233445566778899")));

		X509Certificate[] certs = new X509Certificate[] { eeCert, caCert };

		DSignJar dialog = new DSignJar(new JFrame(), kp.getPrivate(), KeyPairType.RSA, "signature name", certs,
				provider, signer);
		DialogViewer.run(dialog);
	}
}