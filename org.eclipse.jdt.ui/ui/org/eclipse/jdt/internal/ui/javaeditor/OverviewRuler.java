package org.eclipse.jdt.internal.ui.javaeditor;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */



import java.util.Iterator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.TextEvent;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelListener;


import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.ui.JavaPlugin;



/**
 * 
 */
public class OverviewRuler {
	
	/**
	 * Internal listener class.
	 */
	class InternalListener implements ITextListener, IAnnotationModelListener {
		
		/*
		 * @see ITextListener#textChanged
		 */
		public void textChanged(TextEvent e) {		
			if (fTextViewer != null && e.getDocumentEvent() == null && e.getViewerRedrawState()) {
				// handle only changes of visible document
				redraw();
			}
		}
		
		/*
		 * @see IAnnotationModelListener#modelChanged(IAnnotationModel)
		 */
		public void modelChanged(IAnnotationModel model) {
			update();
		}
	}
	
	/**
	 * Filters problems based on their types.
	 */
	class FilterIterator implements Iterator {
		
		private Iterator fIterator;
		private int fType;
		private Annotation fNext;
		
		public FilterIterator(int type) {
			fType= type;
			if (fModel != null) {
				fIterator= fModel.getAnnotationIterator();
				skip();
			}
		}
		
		private void skip() {
			while (fIterator.hasNext()) {
				fNext= (Annotation) fIterator.next();
				int type= getType(fNext);
				if ((fType == ALL && type != UNKNOWN) || fType == type)
					return;
			}
			fNext= null;
		}
		
		/*
		 * @see Iterator#hasNext()
		 */
		public boolean hasNext() {
			return fNext != null;
		}
		/*
		 * @see Iterator#next()
		 */
		public Object next() {
			try {
				return fNext;
			} finally {
				if (fModel != null)
					skip();
			}
		}
		/*
		 * @see Iterator#remove()
		 */
		public void remove() {
			throw new UnsupportedOperationException();
		}
	};
	
	
	
	/** Problem types */
	private static final int ALL= -1;
	private static final int COMPILE_WARNING= 0;
	private static final int COMPILE_ERROR= 1;
	private static final int COMPILE_TASK= 2;
	private static final int TEMPORARY_WARNING= 3;
	private static final int TEMPORARY_ERROR= 4;
	private static final int TEMPORARY_TASK= 5;
	private static final int UNKNOWN= 6;
	
	/** Color table */
	private static final RGB[][] COLORS= new RGB[][] {
															/* fill */						/* stroke */
		/* compile warning */	{ new RGB(248, 218, 114),	new RGB(139, 109, 7) },
		/* compile error */		{ new RGB(255, 140, 140),	new RGB(255, 0, 0) },
		/* compile task */			{ new RGB(0, 128, 255),		new RGB(176, 216 , 255) },
		/* temporary warning */ { new RGB(255, 255, 206),	new RGB(244, 200, 45) },
		/* temporary error */	{ new RGB(240, 230, 230),	new RGB(200, 100, 100) },
		/* temporary task */		{ new RGB(159, 207, 255),	new RGB(255, 255, 255) }
	};
	
	/** drawing layers */
	private static final int[] LAYERS= new int[] { COMPILE_TASK, TEMPORARY_TASK, COMPILE_WARNING, TEMPORARY_WARNING, COMPILE_ERROR, TEMPORARY_ERROR };
	
	private static final int INSET= 2;
	private static final int PROBLEM_HEIGHT_MIN= 4;
	private static boolean PROBLEM_HEIGHT_SCALABLE= false;


	
	/** The model of the overview ruler */
	private IAnnotationModel fModel;
	/** The view to which this ruler is connected */
	private ITextViewer fTextViewer;
	/** The ruler's canvas */
	private Canvas fCanvas;
	/** The drawable for double buffering */
	private Image fBuffer;
	/** The internal listener */
	private InternalListener fInternalListener= new InternalListener();
	/** The width of this vertical ruler */
	private int fWidth;
	/** The hit detection cursor */
	private Cursor fHitDetectionCursor;
	/** The last cursor */
	private Cursor fLastCursor;
	/** Flag indicating the presentation of errors */
	private boolean fShowErrors= false;
	/** Flag indicating the presentation of warnings */
	private boolean fShowWarnings= false;
	/** Flag indicating the presentation of tasks */
	private boolean fShowTasks= false;
	
	
	/**
	 * Constructs a vertical ruler with the given width.
	 *
	 * @param width the width of the vertical ruler
	 */
	public OverviewRuler(int width) {
		fWidth= width;
	}
	
	public Control getControl() {
		return fCanvas;
	}
	
	public int getWidth() {
		return fWidth;
	}
	
	private int getType(Annotation annotation) {
		if (annotation instanceof IProblemAnnotation) {
			IProblemAnnotation pa= (IProblemAnnotation) annotation;
			
			if (!pa.isRelevant())
				return UNKNOWN;
			
			if (pa.isProblem()) {
				
				if (pa.isError())
					return pa.isTemporary() ? TEMPORARY_ERROR : COMPILE_ERROR;
				
				if (pa.isWarning())
					return pa.isTemporary() ? TEMPORARY_WARNING : COMPILE_WARNING;
			
			} else {
				return pa.isTemporary() ? TEMPORARY_TASK : COMPILE_TASK;
			}
		}
		
		return UNKNOWN;
	}
	
	public void setModel(IAnnotationModel model) {
		if (model != fModel || model != null) {
			
			if (fModel != null)
				fModel.removeAnnotationModelListener(fInternalListener);
			
			fModel= model;
			
			if (fModel != null)
				fModel.addAnnotationModelListener(fInternalListener);
			
			update();
		}
	}	
	
	public Control createControl(Composite parent, ITextViewer textViewer) {
		
		fTextViewer= textViewer;
		
		fHitDetectionCursor= new Cursor(parent.getDisplay(), SWT.CURSOR_HAND);
		fCanvas= new Canvas(parent, SWT.NO_BACKGROUND);
		
		fCanvas.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent event) {
				if (fTextViewer != null)
					doubleBufferPaint(event.gc);
			}
		});
		
		fCanvas.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent event) {
				handleDispose();
				fTextViewer= null;		
			}
		});
		
		fCanvas.addMouseListener(new MouseAdapter() {
			public void mouseDown(MouseEvent event) {
				handleMouseDown(event);
			}
		});
		
		fCanvas.addMouseMoveListener(new MouseMoveListener() {
			public void mouseMove(MouseEvent event) {
				handleMouseMove(event);
			}
		});
		
		if (fTextViewer != null)
			fTextViewer.addTextListener(fInternalListener);
		
		return fCanvas;
	}
	
	/**
	 * Disposes the ruler's resources.
	 */
	private void handleDispose() {
		
		if (fTextViewer != null) {
			fTextViewer.removeTextListener(fInternalListener);
			fTextViewer= null;
		}

		if (fModel != null)
			fModel.removeAnnotationModelListener(fInternalListener);

		if (fBuffer != null) {
			fBuffer.dispose();
			fBuffer= null;
		}
		
		if (fHitDetectionCursor != null) {
			fHitDetectionCursor.dispose();
			fHitDetectionCursor= null;
		}
	}

	/**
	 * Double buffer drawing.
	 */
	private void doubleBufferPaint(GC dest) {
		
		Point size= fCanvas.getSize();
		
		if (size.x <= 0 || size.y <= 0)
			return;
		
		if (fBuffer != null) {
			Rectangle r= fBuffer.getBounds();
			if (r.width != size.x || r.height != size.y) {
				fBuffer.dispose();
				fBuffer= null;
			}
		}
		if (fBuffer == null)
			fBuffer= new Image(fCanvas.getDisplay(), size.x, size.y);
			
		GC gc= new GC(fBuffer);
		try {
			gc.setBackground(fCanvas.getBackground());
			gc.fillRectangle(0, 0, size.x, size.y);
			doPaint(gc);
		} finally {
			gc.dispose();
		}
		
		dest.drawImage(fBuffer, 0, 0);
	}
	
	private Color getColor(RGB rgb) {
		JavaTextTools textTools= JavaPlugin.getDefault().getJavaTextTools();
		return textTools.getColorManager().getColor(rgb);
	}
	
	private void doPaint(GC gc) {
		
		if (fTextViewer == null)
			return;
			
		Rectangle r= new Rectangle(0, 0, 0, 0);
		int yy, hh= PROBLEM_HEIGHT_MIN;
		
		
		IDocument document= fTextViewer.getDocument();
		IRegion visible= fTextViewer.getVisibleRegion();
		
		StyledText textWidget= fTextViewer.getTextWidget();
		int maxLines= textWidget.getLineCount();
				
		Point size= fCanvas.getSize();
		int writable= maxLines * textWidget.getLineHeight();
		if (size.y > writable)
			size.y= writable;
		
		for (int l= 0 ; l < LAYERS.length; l++) {
			
			if (skipLayer(LAYERS[l]))
				continue;
			
			Iterator e= new FilterIterator(LAYERS[l]);
			Color fill= getColor(COLORS[LAYERS[l]][0]);
			Color stroke= getColor(COLORS[LAYERS[l]][1]);
			
			for (int i= 0; e.hasNext(); i++) {
				
				Annotation a= (Annotation) e.next();
				Position p= fModel.getPosition(a);
				
				if (p == null || !p.overlapsWith(visible.getOffset(), visible.getLength()))
					continue;
					
				int problemOffset= Math.max(p.getOffset(), visible.getOffset());
				int problemEnd= Math.min(p.getOffset() + p.getLength(), visible.getOffset() + visible.getLength());
				int problemLength= problemEnd - problemOffset;				
				
				try {
					
					int startLine= textWidget.getLineAtOffset(problemOffset - visible.getOffset());
					yy= (startLine * size.y) / maxLines;
					
					if (PROBLEM_HEIGHT_SCALABLE) {
						int numbersOfLines= document.getNumberOfLines(problemOffset, problemLength);
						hh= (numbersOfLines * size.y) / maxLines;
						if (hh < PROBLEM_HEIGHT_MIN)
							hh= PROBLEM_HEIGHT_MIN;
					}
						
					if (fill != null) {
						gc.setBackground(fill);
						gc.fillRectangle(INSET, yy, size.x-(2*INSET), hh);
					}
					
					if (stroke != null) {
						gc.setForeground(stroke);
						r.x= INSET;
						r.y= yy;
						r.width= size.x - (2 * INSET) - 1;
						r.height= hh;
						gc.setLineWidth(1);
						gc.drawRectangle(r);
					}
				} catch (BadLocationException x) {
				}
			}
		}
	}
	
		
	/**
	 * Thread-safe implementation.
	 * Can be called from any thread.
	 */
	public void update() {
		if (fCanvas != null && !fCanvas.isDisposed()) {
			Display d= fCanvas.getDisplay();
			if (d != null) {
				d.asyncExec(new Runnable() {
					public void run() {
						redraw();
					}
				});
			}	
		}
	}
	
	/**
	 * Redraws the overview ruler.
	 */
	private void redraw() {
		if (fCanvas != null && !fCanvas.isDisposed()) {
			GC gc= new GC(fCanvas);
			doubleBufferPaint(gc);
			gc.dispose();
		}
	}
	
	private int[] toLineNumbers(int y_coordinate) {
		
		IRegion visible= fTextViewer.getVisibleRegion();
		int lineNumber= 0;
		try {
			lineNumber= fTextViewer.getDocument().getLineOfOffset(visible.getOffset());
		} catch (BadLocationException x) {
		}
					
		StyledText textWidget=  fTextViewer.getTextWidget();
		int maxLines= textWidget.getContent().getLineCount();
		
		Point size= fCanvas.getSize();
		int writable= maxLines * textWidget.getLineHeight();
		if (size.y > writable)
			size.y= writable;
		
		int[] lines= new int[2];
		
		int pixel= Math.max(y_coordinate - 1, 0);
		lines[0]=  lineNumber + (pixel * maxLines) / size.y;
		
		pixel= Math.min(size.y, y_coordinate + 1);
		lines[1]=  lineNumber + (pixel * maxLines) / size.y;
		
		return lines;
	}
	
	private Position getProblemPositionAt(int[] lineNumbers) {
		
		Position found= null;
		
		try {
			IDocument d= fTextViewer.getDocument();
			IRegion line= d.getLineInformation(lineNumbers[0]);
			int start= line.getOffset();
			
			line= d.getLineInformation(lineNumbers[lineNumbers.length - 1]);
			int end= line.getOffset() + line.getLength();
			
			Iterator e= new FilterIterator(ALL);
			while (e.hasNext()) {
				Annotation a= (Annotation) e.next();
				Position p= fModel.getPosition(a);
				if (start <= p.getOffset() && p.getOffset() < end) {
					if (found == null || p.getOffset() < found.getOffset())
						found= p;
				}
			}
			
		} catch (BadLocationException x) {
		}
		
		return found;
	}
	
	private void handleMouseDown(MouseEvent event) {
		if (fTextViewer != null) {
			int[] lines= toLineNumbers(event.y);
			Position p= getProblemPositionAt(lines);
			if (p != null) {
				fTextViewer.revealRange(p.getOffset(), p.getLength());
				fTextViewer.setSelectedRange(p.getOffset(), p.getLength());
			}
			fTextViewer.getTextWidget().setFocus();
		}
	}
	
	private void handleMouseMove(MouseEvent event) {
		if (fTextViewer != null) {
			int[] lines= toLineNumbers(event.y);
			Position p= getProblemPositionAt(lines);
			Cursor cursor= (p != null ? fHitDetectionCursor : null);
			if (cursor != fLastCursor) {
				fCanvas.setCursor(cursor);
				fLastCursor= cursor;
			}
		}				
	}
	
	public void showErrors(boolean showErrors) {
		fShowErrors= showErrors;
	}
	
	public void showWarnings(boolean showWarnings) {
		fShowWarnings= showWarnings;
	}
	
	public void showTasks(boolean showTasks) {
		fShowTasks= showTasks;
	}
	
	private boolean skipLayer(int layer) {
		if (!fShowErrors && (layer == TEMPORARY_ERROR || layer == COMPILE_ERROR))
			return true;
		if (!fShowWarnings && (layer == TEMPORARY_WARNING || layer == COMPILE_WARNING))
			return true;
		if (!fShowTasks && (layer == TEMPORARY_TASK || layer == COMPILE_TASK))
			return true;
		return false;
	}
}
