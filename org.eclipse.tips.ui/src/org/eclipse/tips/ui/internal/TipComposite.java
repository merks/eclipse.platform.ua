/*******************************************************************************
 * Copyright (c) 2018 Remain Software
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     wim.jongman@remainsoftware.com - initial API and implementation
 *******************************************************************************/
package org.eclipse.tips.ui.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.tips.core.IHtmlTip;
import org.eclipse.tips.core.IUrlTip;
import org.eclipse.tips.core.Tip;
import org.eclipse.tips.core.TipAction;
import org.eclipse.tips.core.TipImage;
import org.eclipse.tips.core.TipProvider;
import org.eclipse.tips.core.internal.LogUtil;
import org.eclipse.tips.core.internal.TipManager;
import org.eclipse.tips.ui.ISwtTip;
import org.eclipse.tips.ui.internal.util.ImageUtil;
import org.eclipse.tips.ui.internal.util.ResourceManager;

@SuppressWarnings("restriction")
public class TipComposite extends Composite implements ProviderSelectionListener {
	private static final int READ_TIMER = 2000;
	private TipProvider fProvider;
	private Browser fBrowser;
	private Slider fSlider;
	private TipManager fTipManager;
	private Tip fCurrentTip;
	private Button fShowAtStart;
	private Button fUnreadOnly;
	private Button fPreviousTipButton;
	private Pattern fGtkHackPattern = Pattern.compile("(.*?)([0-9]+)(.*?)([0-9]+)(.*?)");
	private Composite fSWTComposite;
	private Composite fBrowserComposite;
	private StackLayout fContentStack;
	private Button fMultiActionMenuButton;
	private Composite fNavigationBar;
	private StackLayout fActionStack;
	private Composite fEmptyActionComposite;
	private Composite fSingleActionComposite;
	private Composite fMultiActionComposite;
	private Button fSingleActionButton;
	private Button fMultiActionButton;
	private Composite fContentComposite;
	private List<Image> fActionImages = new ArrayList<>();
	private Menu fActionMenu;

	/**
	 * Constructor.
	 *
	 * @param parent
	 *            the parent
	 * @param style
	 *            the style
	 */
	public TipComposite(Composite parent, int style) {
		super(parent, style);
		GridLayout gridLayout_1 = new GridLayout(1, false);
		gridLayout_1.marginWidth = 2;
		gridLayout_1.marginHeight = 2;
		setLayout(gridLayout_1);

		fContentComposite = new Composite(this, SWT.NONE);
		fContentStack = new StackLayout();
		fContentComposite.setLayout(fContentStack);
		GridData gd_gridComposite = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
		gd_gridComposite.widthHint = 900;
		gd_gridComposite.heightHint = 600;
		// gd_gridComposite.minimumWidth = -1;
		// gd_gridComposite.minimumHeight = -1;
		fContentComposite.setLayoutData(gd_gridComposite);

		fBrowserComposite = new Composite(fContentComposite, SWT.NONE);
		fBrowserComposite.setLayout(new FillLayout(SWT.HORIZONTAL));

		fBrowser = new Browser(fBrowserComposite, SWT.NONE);
		fBrowser.setJavascriptEnabled(true);

		fSWTComposite = new Composite(fContentComposite, SWT.NONE);
		fSWTComposite.setLayout(new FillLayout(SWT.HORIZONTAL));

		fNavigationBar = new Composite(this, SWT.NONE);
		fNavigationBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		GridLayout gl_NavigationBar = new GridLayout(2, false);
		gl_NavigationBar.horizontalSpacing = 0;
		gl_NavigationBar.marginHeight = 0;
		gl_NavigationBar.verticalSpacing = 0;
		gl_NavigationBar.marginWidth = 0;
		fNavigationBar.setLayout(gl_NavigationBar);

		Composite preferenceBar = new Composite(fNavigationBar, SWT.NONE);
		FillLayout fl_composite_3 = new FillLayout(SWT.HORIZONTAL);
		fl_composite_3.marginWidth = 5;
		fl_composite_3.spacing = 5;
		preferenceBar.setLayout(fl_composite_3);

		fShowAtStart = new Button(preferenceBar, SWT.CHECK);
		fShowAtStart.setText("Show tips at startup");
		fShowAtStart.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				fTipManager.setRunAtStartup(fShowAtStart.getSelection());
			}
		});

		fUnreadOnly = new Button(preferenceBar, SWT.CHECK);
		fUnreadOnly.setText("Unread only");
		fUnreadOnly.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				fTipManager.setServeReadTips(!fUnreadOnly.getSelection());
				fPreviousTipButton.setEnabled(fTipManager.mustServeReadTips());
				fSlider.load();
				getNextTip();
			}
		});

		Composite buttonBar = new Composite(fNavigationBar, SWT.NONE);
		buttonBar.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));
		GridLayout gl_buttonBar = new GridLayout(4, true);
		gl_buttonBar.marginHeight = 0;
		buttonBar.setLayout(gl_buttonBar);

		Composite actionComposite = new Composite(buttonBar, SWT.NONE);
		fActionStack = new StackLayout();
		actionComposite.setLayout(fActionStack);
		actionComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		fSingleActionComposite = new Composite(actionComposite, SWT.NONE);
		GridLayout gl_SingleActionComposite = new GridLayout(1, false);
		gl_SingleActionComposite.marginWidth = 0;
		fSingleActionComposite.setLayout(gl_SingleActionComposite);

		fSingleActionButton = new Button(fSingleActionComposite, SWT.NONE);
		fSingleActionButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		fSingleActionButton.setText("More...");
		fSingleActionButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				runTipAction(fCurrentTip.getActions().get(0));
			}
		});

		fMultiActionComposite = new Composite(actionComposite, SWT.NONE);
		GridLayout gl_MultiActionComposite = new GridLayout(2, false);
		gl_MultiActionComposite.marginWidth = 0;
		gl_MultiActionComposite.verticalSpacing = 0;
		gl_MultiActionComposite.horizontalSpacing = 0;
		fMultiActionComposite.setLayout(gl_MultiActionComposite);

		fMultiActionButton = new Button(fMultiActionComposite, SWT.NONE);
		fMultiActionButton.setText("New Button");
		fMultiActionButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				runTipAction(fCurrentTip.getActions().get(0));
			}
		});

		fMultiActionMenuButton = new Button(fMultiActionComposite, SWT.NONE);
		fMultiActionMenuButton.setImage(ResourceManager.getPluginImage("org.eclipse.tips.ui", "icons/popup_menu.gif"));
		fMultiActionMenuButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showActionMenu();
			}
		});

		fEmptyActionComposite = new Composite(actionComposite, SWT.NONE);
		fEmptyActionComposite.setLayout(new FillLayout(SWT.HORIZONTAL));

		fPreviousTipButton = new Button(buttonBar, SWT.NONE);
		fPreviousTipButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		fPreviousTipButton.setText("Previous Tip");
		fPreviousTipButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				getPreviousTip();
			}
		});
		fPreviousTipButton.setEnabled(false);

		Button btnNextTip = new Button(buttonBar, SWT.NONE);
		btnNextTip.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnNextTip.setText("Next Tip");
		btnNextTip.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getNextTip();
			}
		});

		Button btnClose = new Button(buttonBar, SWT.NONE);
		btnClose.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				getParent().dispose();
			}
		});
		btnClose.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnClose.setText("Close");

		Label label_1 = new Label(this, SWT.SEPARATOR | SWT.HORIZONTAL);
		label_1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));

		fSlider = new Slider(this, SWT.NONE);
		GridLayout gridLayout = (GridLayout) fSlider.getLayout();
		gridLayout.verticalSpacing = 0;
		gridLayout.marginWidth = 0;
		gridLayout.horizontalSpacing = 0;
		fSlider.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, false, false, 1, 1));
		fContentStack.topControl = fBrowserComposite;
		fSlider.addTipProviderListener(this);
	}

	private void showActionMenu() {
		Rectangle rect = fMultiActionButton.getBounds();
		Point pt = new Point(rect.x - 1, rect.y + rect.height);
		pt = fMultiActionButton.toDisplay(pt);
		fActionMenu.setLocation(pt.x, pt.y);
		fActionMenu.setVisible(true);
	}

	private void runTipAction(TipAction tipAction) {
		Job job = new Job("Running " + tipAction.getTooltip()) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					tipAction.getRunner().run();
				} catch (Exception e) {
					IStatus status = LogUtil.error(getClass(), e);
					fTipManager.log(status);
					return status;
				}
				return Status.OK_STATUS;
			}
		};
		job.setUser(true);
		job.schedule();
	}

	/**
	 * Sets the selected provider.
	 *
	 * @param provider
	 *            the {@link TipProvider}
	 */
	public void setProvider(TipProvider provider) {
		fProvider = provider;
		fSlider.setTipProvider(provider);
		getCurrentTip();
	}

	/**
	 * Schedules a TimerTask that is executed after {@value #READ_TIMER}
	 * milliseconds after which the tip is marked as read.
	 */
	private void hitTimer() {
		Tip timerTip = fCurrentTip;
		Timer timer = new Timer("Tip read timer");
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				if (timerTip == fCurrentTip) {
					fTipManager.setAsRead(timerTip);
					fSlider.updateButtons();
				}
				timer.cancel();
			}
		}, READ_TIMER);
	}

	private void getPreviousTip() {
		processTip(fProvider.getPreviousTip());
	}

	private void getNextTip() {
		if (fProvider.getTips().isEmpty() && !fTipManager.getProviders().isEmpty()) {
			fProvider.getNextTip(); // advance current tip
			for (TipProvider provider : fTipManager.getProviders()) {
				if (!provider.getTips().isEmpty()) {
					setProvider(provider);
					break;
				}
			}
		}
		processTip(fProvider.getNextTip());
	}

	private void getCurrentTip() {
		processTip(fProvider.getCurrentTip());
	}

	private void processTip(Tip tip) {
		fCurrentTip = tip;
		hitTimer();
		enableActionButtons(tip);
		prepareForHTML();
		loadContent(tip);
	}

	private void loadContent(Tip tip) {
		if (tip instanceof ISwtTip) {
			loadContentSWT(tip);
		} else if (tip instanceof IHtmlTip) {
			loadContentHtml((IHtmlTip) tip);
		} else if (tip instanceof IUrlTip) {
			loadContentUrl((IUrlTip) tip);
		} else {
			fTipManager.log(LogUtil.error(getClass(), "Unknown Tip implementation: " + tip)) ;
		}
		fContentComposite.requestLayout();
	}

	private void loadContentHtml(IHtmlTip tip) {
		try {
			fBrowser.setText(getScaling() + getHTML(tip).trim());
		} catch (IOException e) {
			fTipManager.log(LogUtil.error(getClass(), e));
		}
	}

	private void loadContentUrl(IUrlTip tip) {
		try {
			fBrowser.setUrl(FileLocator.resolve(tip.getURL()).toString());
		} catch (IOException e) {
			fTipManager.log(LogUtil.error(getClass(), e));
		}
	}

	private void loadContentSWT(Tip tip) {
		for (Control control : fSWTComposite.getChildren()) {
			control.dispose();
		}
		fContentStack.topControl = fSWTComposite;
		((ISwtTip) tip).createControl(fSWTComposite);
		fSWTComposite.requestLayout();
	}

	private void prepareForHTML() {
		fContentStack.topControl = fBrowserComposite;
		loadTimeOutScript();
		fBrowserComposite.requestLayout();
	}

	/**
	 * Sets content in the browser that displays a message after 500ms if the Tip
	 * could not load fast enough.
	 */
	private void loadTimeOutScript() {
		fBrowser.setText(getScaling() + getLoadingScript(500));
		while (!isDisposed()) {
			if (!getDisplay().readAndDispatch()) {
				break;
			}
		}
	}

	private void enableActionButtons(Tip tip) {
		disposeActionImages();
		if (tip.getActions().isEmpty()) {
			fActionStack.topControl = fEmptyActionComposite;
		} else if (tip.getActions().size() == 1) {
			TipAction action = tip.getActions().get(0);
			fActionStack.topControl = fSingleActionComposite;
			fSingleActionButton.setImage(getActionImage(action.getTipImage()));
			fSingleActionButton.setText(action.getText());
			fSingleActionButton.setToolTipText(action.getTooltip());
			blinkActionComposite(fSingleActionComposite);
		} else {
			TipAction action = tip.getActions().get(0);
			fActionStack.topControl = fMultiActionComposite;
			fMultiActionButton.setImage(getActionImage(tip.getActions().get(0).getTipImage()));
			fMultiActionButton.setText(action.getText());
			fMultiActionButton.setToolTipText(action.getTooltip());
			loadActionMenu(tip);
			blinkActionComposite(fMultiActionComposite);
		}
		fEmptyActionComposite.getParent().requestLayout();
		fNavigationBar.requestLayout();
	}

	private void disposeActionImages() {
		fActionImages.forEach(img -> img.dispose());
	}

	private void loadActionMenu(Tip pTip) {
		if (fActionMenu != null) {
			fActionMenu.dispose();
		}
		fActionMenu = new Menu(fContentComposite.getShell(), SWT.POP_UP);
		pTip.getActions().subList(1, pTip.getActions().size()).forEach(action -> {
			MenuItem item = new MenuItem(fActionMenu, SWT.PUSH);
			item.setText(action.getText());
			item.setToolTipText(action.getTooltip());
			item.setText(action.getText());
			item.setImage(getActionImage(action.getTipImage()));
			item.addListener(SWT.Selection, e -> runTipAction(action));
		});
	}

	private void blinkActionComposite(Composite composite) {
		Color bg = composite.getParent().getBackground();
		Color red = getDisplay().getSystemColor(SWT.COLOR_RED);
		boolean flip = false;
		for (int i = 1; i < 6; i++) {
			boolean flop = flip;
			if (!composite.isDisposed()) {
				getDisplay().timerExec(i * 200, () -> {
					if (!composite.isDisposed()) {
						composite.getParent().setBackground(flop ? bg : red);
					}
				});
			}
			flip = !flip;
		}
	}

	private Image getActionImage(TipImage tipImage) {
		if (tipImage == null) {
			return null;
		}
		try {
			Image image = new Image(getDisplay(), ImageUtil.decodeToImage(tipImage.getBase64Image()));
			if (image != null) {
				fActionImages.add(image);
				return image;
			}
		} catch (IOException e) {
			fTipManager.log(LogUtil.error(getClass(), e));
		}
		return null;
	}

	/**
	 * Get the timeout script in case the tips takes too to load.
	 *
	 * @param timeout
	 *            the timeout in milliseconds
	 * @return the script
	 */
	private static String getLoadingScript(int timeout) {
		return "<style>div{position:fixed;top:50%;left:40%}</style>" + "<div id=\"txt\"></div>"
				+ "<script>var wss=function(){document.getElementById(\"txt\").innerHTML=\"Loading next Tip...\"};window.setTimeout(wss,"
				+ timeout + ");</script>";
	}

	private String getHTML(IHtmlTip tip) throws IOException {
		String encodedImage = encodeImage(tip);
		return tip.getHTML() + encodedImage;
	}

	private static String getScaling() {
		if (Platform.isRunning() && Platform.getWS().startsWith("gtk")) {
			int deviceZoom = DPIUtil.getDeviceZoom();
			int zoom = deviceZoom;
			return "<style>" + "body {" + "  zoom: " + zoom + "%;" + "}</style> ";
		}
		return "";
	}

	private String encodeImage(IHtmlTip tip) throws IOException {
		TipImage image = tip.getImage();
		if (image == null) {
			return "";
		}
		return encodeImageFromBase64(image);
	}

	private String encodeImageFromBase64(TipImage image) throws IOException {
		int width = fBrowser.getClientArea().width;
		int height = Math.min(fBrowser.getClientArea().height / 2, (2 * (width / 3)));
		String attributes = gtkHack(image.getIMGAttributes(width, height).trim());
		String encoded = "" //
				+ "<center> <img " //
				+ attributes //
				+ " src=\"" //
				+ image.getBase64Image() //
				+ "\"></center><br/>";
		return encoded;
	}

	private String gtkHack(String imageAttribute) {
		if (!Platform.isRunning()) {
			return imageAttribute;
		}
		if (!Platform.getWS().startsWith("gtk")) {
			return imageAttribute;
		}
		Matcher m = fGtkHackPattern.matcher(imageAttribute);
		if (!m.matches()) {
			return imageAttribute;
		}
		return m.group(1) + (Integer.parseInt(m.group(2)) * 120 / 100) + m.group(3)
				+ (Integer.parseInt(m.group(4)) * 120 / 100) + m.group(5);
	}

	@Override
	protected void checkSubclass() {
	}

	/**
	 * @return the {@link Browser} widget
	 */
	public Browser getBrowser() {
		return fBrowser;
	}

	/**
	 * @return the {@link Slider} widget
	 */
	public Slider getSlider() {
		return fSlider;
	}

	@Override
	public void selected(TipProvider provider) {
		setProvider(provider);
	}

	/**
	 * Sets the {@link TipManager}
	 *
	 * @param tipManager
	 *            the {@link TipManager} that opened the dialog.
	 */
	public void setTipManager(TipManager tipManager) {
		fTipManager = tipManager;

		getDisplay().syncExec(() -> {
			fSlider.setTipManager(fTipManager);
			fShowAtStart.setSelection(fTipManager.isRunAtStartup());
			fUnreadOnly.setSelection(!fTipManager.mustServeReadTips());
			fPreviousTipButton.setEnabled(fTipManager.mustServeReadTips());
		});
	}

	@Override
	public void dispose() {
		disposeActionImages();
		super.dispose();
	}
}