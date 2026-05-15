/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2013 jOpenDocument, by ILM Informatique. All rights reserved.
 * 
 * The contents of this file are subject to the terms of the GNU
 * General Public License Version 3 only ("GPL").  
 * You may not use this file except in compliance with the License. 
 * You can obtain a copy of the License at http://www.gnu.org/licenses/gpl-3.0.html
 * See the License for the specific language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each file.
 * 
 */

package org.jopendocument.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;

import org.jopendocument.model.OpenDocument;
import org.jopendocument.print.DocumentPrinter;
import org.jopendocument.renderer.ODTRenderer;

public class ODSViewerPanel extends JPanel implements ProgressListener {
    /**
     * 
     */
    private static final long serialVersionUID = -6113257667157151508L;
    private final ODTRenderer renderer;
    private int mode;
    private int zoom = 100;
    private static final int MODE_PAGE = 0;
    private static final int MODE_WIDTH = 1;
    private static final int MODE_ZOOM = 2;
    private JScrollPane scroll;
    private final JPanel viewer = new JPanel();
    private final JTextField textFieldZoomValue = new JTextField(5);
    private int currentPageIndex = 0;
    private final JPanel tools = new JPanel();
    private JProgressBar progressBar;
    private DocumentPrinter printListener;
    private List<JComponent> customToolsComponents = new ArrayList<>();
    private boolean showSaveAsButton = true;

    public ODSViewerPanel(final OpenDocument doc) {
        this(doc, null);
    }

    public ODSViewerPanel(final OpenDocument doc, final boolean ignoreMargin) {
        this(doc, null, ignoreMargin);
    }

    public ODSViewerPanel(final OpenDocument doc, final DocumentPrinter printListener) {
        this(doc, printListener, true);
    }

    public ODSViewerPanel(final String url, String odspXml, final DocumentPrinter printListener, final boolean ignoreMargin) {
        this.renderer = new ODTRenderer(url, odspXml);
        this.renderer.addProgressListener(this);
        this.renderer.setIgnoreMargins(ignoreMargin);
        this.printListener = printListener;
        init(ignoreMargin);
        this.tools.setLayout(new WrapLayout());
        this.tools.add(new JLabel(Messages.getString("ODSViewerPanel.loading")));
        this.progressBar = new JProgressBar(0, 100);
        this.tools.add(this.progressBar);

        this.setPreferredSize(new Dimension(750, 700));
        this.setMinimumSize(new Dimension(740, 300));
    }

    public ODSViewerPanel(final OpenDocument doc, final DocumentPrinter printListener, final boolean ignoreMargin) {
        this.printListener = printListener;
        this.renderer = new ODTRenderer(doc);
        this.renderer.setIgnoreMargins(ignoreMargin);
        updateMode(MODE_ZOOM, this.zoom);

        init(ignoreMargin);
        // init tools after init because WrapLayout must know the size to compute its height
        initTools(doc);
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateMode(ODSViewerPanel.this.mode, ODSViewerPanel.this.zoom);
            }
        });
    }

    private void init(final boolean ignoreMargin) {

        Toolkit.getDefaultToolkit().setDynamicLayout(false);
        this.setOpaque(false);

        // Viewer
        this.viewer.setOpaque(false);
        this.viewer.setBackground(Color.DARK_GRAY);
        this.viewer.setLayout(null);
        this.renderer.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        this.viewer.add(this.renderer);
        this.setLayout(new BorderLayout());
        this.add(this.tools, BorderLayout.NORTH);

        this.scroll = new JScrollPane(this.viewer);
        this.scroll.setOpaque(false);
        this.scroll.getHorizontalScrollBar().setUnitIncrement(30);
        this.scroll.getVerticalScrollBar().setUnitIncrement(30);
        this.scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        this.scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);

        ((JComponent) this.scroll.getViewport().getView()).setOpaque(false);
        this.add(this.scroll, BorderLayout.CENTER);
        // important to set the size for Wraplayout height computation
        this.setSize(new Dimension(this.scroll.getPreferredSize().width, this.scroll.getPreferredSize().height));

    }

    private void initTools(final OpenDocument doc) {
        this.tools.setLayout(new WrapLayout());
        this.tools.removeAll();
        final JButton buttonTailleReelle = new JButton(Messages.getString("ODSViewerPanel.normalSize"));
        buttonTailleReelle.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateMode(MODE_ZOOM, 100);
            }
        });
        this.tools.add(buttonTailleReelle);
        final JButton buttonFullPage = new JButton(Messages.getString("ODSViewerPanel.fitPage"));
        buttonFullPage.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (ODSViewerPanel.this.mode != MODE_PAGE) {
                    int width = (int) ODSViewerPanel.this.scroll.getViewportBorderBounds().getWidth();
                    int height = (int) ODSViewerPanel.this.scroll.getViewportBorderBounds().getHeight();
                    final double resizeW = ODSViewerPanel.this.renderer.getPageWidth() / width;
                    final double resizeH = ODSViewerPanel.this.renderer.getPageHeight() / height;
                    double resize = resizeH;
                    if (resizeW > resizeH) {
                        resize = resizeW;
                    }
                    updateMode(MODE_PAGE, (int) ((100 * 360) / resize));
                }
            }
        });
        this.tools.add(buttonFullPage);
        final JButton buttonFullWidth = new JButton(Messages.getString("ODSViewerPanel.fitWidth"));
        buttonFullWidth.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int width = (int) (ODSViewerPanel.this.scroll.getViewportBorderBounds().getWidth());
                final double resizeW = ODSViewerPanel.this.renderer.getPageWidth() / width;
                updateMode(MODE_WIDTH, (int) ((100 * 360) / resizeW));
            }
        });
        this.tools.add(buttonFullWidth);

        final JButton buttonZoomOut = new JButton("-");
        buttonZoomOut.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (ODSViewerPanel.this.zoom > 30) {
                    updateMode(ODSViewerPanel.this.mode, ODSViewerPanel.this.zoom - 20);
                }
            }
        });
        this.tools.add(buttonZoomOut);
        this.textFieldZoomValue.setEditable(false);
        this.tools.add(this.textFieldZoomValue);
        final JButton buttonZoomIn = new JButton("+");
        buttonZoomIn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int z = ODSViewerPanel.this.zoom + 20;
                if (z > 400)
                    z = 400;
                updateMode(ODSViewerPanel.this.mode, z);
            }
        });
        this.tools.add(buttonZoomIn);
        if (doc.getPrintedPageCount() > 1) {
            final JTextField page = new JTextField(5);
            page.setHorizontalAlignment(JTextField.CENTER);
            JButton previousButton = new JButton("<");
            previousButton.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    if (ODSViewerPanel.this.currentPageIndex > 0) {
                        ODSViewerPanel.this.currentPageIndex--;
                        updatePage(ODSViewerPanel.this.currentPageIndex);
                        updatePageCount(doc, page);
                    }

                }

            });
            JButton nextButton = new JButton(">");
            nextButton.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    if (ODSViewerPanel.this.currentPageIndex < doc.getPrintedPageCount() - 1) {
                        ODSViewerPanel.this.currentPageIndex++;
                        updatePage(ODSViewerPanel.this.currentPageIndex);
                        updatePageCount(doc, page);
                    }

                }

            });
            this.tools.add(previousButton);
            updatePageCount(doc, page);
            this.tools.add(page);
            this.tools.add(nextButton);

        }
        if (this.showSaveAsButton) {
            JButton buttonSaveAs = new JButton(Messages.getString("ODSViewerPanel.saveAs"));
            buttonSaveAs.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    File file = doc.getLoadedFile();

                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setSelectedFile(file);
                    fileChooser.setDialogTitle(Messages.getString("ODSViewerPanel.saveAs"));

                    int userSelection = fileChooser.showSaveDialog(ODSViewerPanel.this);

                    if (userSelection == JFileChooser.APPROVE_OPTION) {
                        File fileToSave = fileChooser.getSelectedFile();
                        InputStream is = null;
                        OutputStream os = null;
                        try {
                            is = new FileInputStream(file);
                            os = new FileOutputStream(fileToSave);
                            byte[] buffer = new byte[4096];
                            int length;
                            while ((length = is.read(buffer)) > 0) {
                                os.write(buffer, 0, length);
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        } finally {

                            try {
                                if (is != null)
                                    is.close();
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                            try {
                                if (os != null)
                                    os.close();
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                        }
                    }

                }
            });
            this.tools.add(buttonSaveAs);
        }
        if (this.printListener != null) {
            final JButton buttonPrint = new JButton(Messages.getString("ODSViewerPanel.print"));
            buttonPrint.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    ODSViewerPanel.this.printListener.print(Arrays.asList(doc));
                }
            });
            this.tools.add(buttonPrint);
        }

        for (JComponent c : this.customToolsComponents) {
            this.tools.add(c);
        }
    }

    public void addTool(JComponent t) {
        this.customToolsComponents.add(t);
        initTools(this.renderer.getDocument());
    }

    /**
     * @param doc
     * @param page
     */
    private void updatePageCount(final OpenDocument doc, final JTextField page) {
        page.setText((this.currentPageIndex + 1) + "/" + doc.getPrintedPageCount());
    }

    protected void updatePage(int i) {
        this.renderer.setCurrentPage(i);
    }

    private void updateMode(int m, int zoom_value) {
        if (zoom_value < 1) {
            // minimal zoom is 1
            zoom_value = 1;
        }
        this.mode = m;
        this.zoom = zoom_value;
        this.textFieldZoomValue.setText(this.zoom + " %");

        this.renderer.setResizeFactor(((100 * 360) / zoom_value));

        int w = this.renderer.getPageWidthInPixel();

        int h = this.renderer.getPageHeightInPixel();

        int posx = 0;
        int posy = 0;
        if (this.scroll != null) {
            posx = (this.scroll.getViewportBorderBounds().width - w) / 2;
            posy = (this.scroll.getViewportBorderBounds().height - h) / 2;
        }
        if (posy > 10)
            posy = 10;
        if (posx < 0)
            posx = 0;
        if (posy < 0)
            posy = 0;
        this.renderer.setLocation(posx, posy);
        // final int renderedHeight = renderer.getPrintHeightInPixel();
        // renderer.setSize(renderer.getPrintWidthInPixel(), renderedHeight);
        final Dimension size = new Dimension(w, h);
        this.viewer.setPreferredSize(size);
        // Let the scroll pane know to update itself
        // and its scrollbars.
        this.viewer.revalidate();
        repaint();

    }

    public ODTRenderer getRenderer() {
        return this.renderer;
    }

    @Override
    public void progressUpdate(int percent) {
        this.progressBar.setValue(percent);
        if (percent >= 100) {
            updateMode(MODE_ZOOM, this.zoom);
            this.renderer.removeProgressListener(this);
            this.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    updateMode(ODSViewerPanel.this.mode, ODSViewerPanel.this.zoom);

                }

            });

            initTools(this.renderer.getDocument());
            this.revalidate();
        }
    }

    public void showSaveAsButton(boolean b) {
        this.showSaveAsButton = b;
    }
}
