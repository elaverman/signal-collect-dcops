package com.signalcollect.visualization;

import com.signalcollect.*;
import edu.uci.ics.jung.algorithms.layout.FRLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse;
import edu.uci.ics.jung.visualization.layout.LayoutTransition;
import edu.uci.ics.jung.visualization.renderers.Renderer.VertexLabel.Position;
import edu.uci.ics.jung.visualization.util.Animator;
import javax.swing.BoxLayout;
import org.apache.commons.collections15.Transformer;

/**
 * 
 * @author stutz
 */
public class JungPanel extends javax.swing.JPanel {

	private static final long serialVersionUID = 3093499981293625793L;

	com.signalcollect.visualization.ComputeGraphInspector cgi;

	public void setComputeGraphInspector(ComputeGraphInspector cgi) {
		this.cgi = cgi;
	}

	/** Creates new form JungPanel */
	public JungPanel() {
		initComponents();
		Transformer<Vertex, String> vertexLabeler = new Transformer<Vertex, String>() {
			public String transform(Vertex v) {
				return "<html><b>" + v.toString() + "</b></html>";
			}
		};
		Transformer<Edge, String> edgeLabeler = new Transformer<Edge, String>() {
			public String transform(Edge e) {
				Object signal = cgi.getMostRecentSignal(e.id());
				if (signal != null) {
					return "<html><b>" + e.getClass().getSimpleName() + "(signal=" + signal.toString() + ")</b></html>";
				} else {
					return "<html><b>" + e.getClass().getSimpleName() + "(signal=None)</b></html>";
				}
			}
		};
		vv.getRenderContext().setVertexLabelTransformer(vertexLabeler);
		vv.getRenderContext().setEdgeLabelTransformer(edgeLabeler);
		vv.getRenderer().getVertexLabelRenderer().setPosition(Position.CNTR);
		DefaultModalGraphMouse<Vertex, Edge> graphMouse = new DefaultModalGraphMouse<Vertex, Edge>();
		vv.setGraphMouse(graphMouse);
		graphMouse.setMode(ModalGraphMouse.Mode.PICKING);
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		add(vv);
	}

	public synchronized void redraw() {
		Layout<Vertex, Edge> layout = new FRLayout<Vertex, Edge>(currentGraph,
				getSize());
		layout.setInitializer(vv.getGraphLayout());
		LayoutTransition<Vertex, Edge> lt = new LayoutTransition<Vertex, Edge>(
				vv, vv.getGraphLayout(), layout);
		Animator animator = new Animator(lt);
		animator.start();

		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		validateTree();
	}

	private Boolean addVertex(Vertex v) {
		if (currentGraph != null && !currentGraph.containsVertex(v)) {
			currentGraph.addVertex(v);
			return true;
		}
		return false;
	}

	private Boolean addEdge(Edge e, Vertex v, Vertex w) {
		if (currentGraph != null && !currentGraph.containsEdge(e)) {
			currentGraph.addEdge(e, v, w);
			return true;
		}
		return false;
	}

	private DirectedSparseGraph<Vertex, Edge> currentGraph = new DirectedSparseGraph<Vertex, Edge>();
	private Layout<Vertex, Edge> layout = new FRLayout<Vertex, Edge>(
			currentGraph, getSize());
	private VisualizationViewer<Vertex, Edge> vv = new VisualizationViewer<Vertex, Edge>(
			layout);

	public void paintVertex(Vertex vertex, Integer depth) {
		// invalidate();
		currentGraph = new DirectedSparseGraph<Vertex, Edge>();
		addVertex(vertex);
		if (depth > 0) {
			addVertices(vertex, depth - 1);
		}
		redraw();
	}

	/*
	 * Breadth first graph traversal.
	 */
	private void addVertices(Vertex vertex, Integer depth) {
		for (Edge edge : cgi.getEdges(vertex)) {
			Vertex neighborVertex = (Vertex) cgi.getVertexWithId(edge.id()
					.targetId());
			addVertex(neighborVertex);
			addEdge(edge, vertex, neighborVertex);
			if (depth > 0) {
				addVertices(neighborVertex, depth - 1);
			}
		}
//		for (Vertex predecessor : (Iterable<Vertex>) cgi
//				.getPredecessors(vertex)) {
//			addVertex(predecessor);
//			for (Edge edge : (Iterable<Edge>) cgi.getEdges(predecessor)) {
//				addEdge(edge, predecessor, vertex);
//			}
//			if (depth > 0) {
//				addVertices(predecessor, depth - 1);
//			}
//		}
	}

	/**
	 * This method is called from within the constructor to initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is always
	 * regenerated by the Form Editor.
	 */
	// <editor-fold defaultstate="collapsed"
	// desc="Generated Code">//GEN-BEGIN:initComponents
	private void initComponents() {

		org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(
				this);
		this.setLayout(layout);
		layout.setHorizontalGroup(layout.createParallelGroup(
				org.jdesktop.layout.GroupLayout.LEADING).add(0, 400,
				Short.MAX_VALUE));
		layout.setVerticalGroup(layout.createParallelGroup(
				org.jdesktop.layout.GroupLayout.LEADING).add(0, 300,
				Short.MAX_VALUE));
	}// </editor-fold>//GEN-END:initComponents
		// Variables declaration - do not modify//GEN-BEGIN:variables
		// End of variables declaration//GEN-END:variables
}