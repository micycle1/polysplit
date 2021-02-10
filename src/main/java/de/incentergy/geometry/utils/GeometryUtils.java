package de.incentergy.geometry.utils;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.algorithm.LineIntersector;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.AffineTransformation;

public final class GeometryUtils {

    private GeometryUtils() {
    }

    /**
     * Computes the intersection between two lines extending to infinity in both directions
     *
     * @param lineA
     * @param lineB
     * @return a point where the lines intersect, or null if they don't (are parallel or overlap)
     *
     * @see https://en.wikipedia.org/wiki/Line%E2%80%93line_intersection
     */
    public static IntersectionCoordinate getIntersectionPoint(LineSegment lineA, LineSegment lineB) {
        double x1 = lineA.p0.x;
        double y1 = lineA.p0.y;
        double x2 = lineA.p1.x;
        double y2 = lineA.p1.y;

        double x3 = lineB.p0.x;
        double y3 = lineB.p0.y;
        double x4 = lineB.p1.x;
        double y4 = lineB.p1.y;

        double det1And2 = det(x1, y1, x2, y2);
        double det3And4 = det(x3, y3, x4, y4);
        double x1LessX2 = x1 - x2;
        double y1LessY2 = y1 - y2;
        double x3LessX4 = x3 - x4;
        double y3LessY4 = y3 - y4;

        double det1Less2And3Less4 = det(x1LessX2, y1LessY2, x3LessX4, y3LessY4);
        if (det1Less2And3Less4 == 0) {
            return null;
        }

        double x = det(det1And2, x1LessX2, det3And4, x3LessX4) / det1Less2And3Less4;
        double y = det(det1And2, y1LessY2, det3And4, y3LessY4) / det1Less2And3Less4;
        return new IntersectionCoordinate(x, y, lineA, lineB);
    }

    private static double det(double a, double b, double c, double d) {
        return a * d - b * c;
    }

    /**
     * Checks if the line intersects any of the edges of given polygon.<br>
     * Start and end points of the line can only touch, but not cross the edges of polygon.
     *
     * @param line line which might intersect the edges of polygon
     * @param polygon the polygon
     * @return true if line intersects at least one edge of the polygon
     */
    public static boolean isIntersectingPolygon(LineSegment line, Polygon polygon) {
        LineIntersector lineIntersector = new RobustLineIntersector();

        List<LineSegment> edges = getLineSegments(polygon.getExteriorRing());
        for (LineSegment edge : edges) {
            lineIntersector.computeIntersection(line.p0, line.p1, edge.p0, edge.p1);
            if (lineIntersector.hasIntersection() && lineIntersector.isProper()) {      // intersection exists and is not one of the endpoints of the line
                return true;
            }
        }
        return false;
    }

    /**
     * Determines a projection of vertex on opposing edge at an angle perpendicular to angle-bisector of the edges
     * @param vertex
     * @param opposingEdge
     * @param intersectionPoint
     * @return
     */
    public static Coordinate getProjectedPoint(Coordinate vertex, LineSegment opposingEdge, IntersectionCoordinate intersectionPoint) {
        if (intersectionPoint != null) {

            if (intersectionPoint.belongsToOneOfTheEdges) {
                // edge case - intersection point lies somewhere on the opposingEdge
                // in this case the distance-based approach does not work, as there could be two points having the same distance - one towards each edge

                /*
                 * If we draw a line perpendicular to the opposingEdge and crossing it at intersection point, two scenarios are possible:
                 *   1) Vertex and oposingEdge falls on different sides of this perpendicularLine
                 *   2) Vertex and oposingEdge falls on one side of this perpendicularLine
                 * In 1st case we can discard the point straight away, as the projection will fall outside the opposingEdge (on the other side of the intersection point)
                 * In 2nd case we can shorten the opposingEdge by removing the part of the edge that lies on the other side of the perpendicular line
                 */
                // create a line perpendicular to the opposingEdge at intersection point (rotate one of the endpoints 90 degrees around intersection point)
                AffineTransformation rotate90degAroundIntersection = AffineTransformation.rotationInstance(1, 0, intersectionPoint.x, intersectionPoint.y);
                Coordinate pointAlongPerpendicularLine = rotate90degAroundIntersection.transform(opposingEdge.p0, new Coordinate());
                LineSegment perpendicularLine = new LineSegment(intersectionPoint, pointAlongPerpendicularLine);

                int orientationIndexOfVertex = perpendicularLine.orientationIndex(vertex);

                if (isPointOnLineSegmentExcludingEndpoints(intersectionPoint, opposingEdge)) {
                    // the intersection point is on the edge
                    int orientationIndexOfP0 = perpendicularLine.orientationIndex(opposingEdge.p0);
                    if (orientationIndexOfVertex == orientationIndexOfP0) {
                        // p0 of opposingEdge is on the same side as the vertex (thus we shorten the segment discarding p1)
                        opposingEdge = new LineSegment(opposingEdge.p0, intersectionPoint);

                    } else {
                        // p1 of opposingEdge is on the same side as the vertex (thus we shorten the segment discarding p0)
                        opposingEdge = new LineSegment(intersectionPoint, opposingEdge.p1);
                    }
                    // proceed as usual using the modified LineSegment

                } else {
                    // the intersection point is outside of the edge
                    int orientationIndexOfEdge = perpendicularLine.orientationIndex(opposingEdge);   // -1 or +1
                    // TODO: need to handle edge pairs better
//                    boolean vertexIsOnPerpendicularLine = orientationIndexOfVertex == 0;             // this is needed to handle cases when both edges are perpendicular to each other
                    boolean vertexIsOnPerpendicularLine = false;
                    if (!vertexIsOnPerpendicularLine && orientationIndexOfVertex != orientationIndexOfEdge) {
                        // projection of vertex is located somewhere on the opposite side of the intersection point (not on the edge)
                        return null;
                    }
                    // otherwise it is on the same side - proceed as usual
                }
            }

            // usual case - when intersection point is somewhere further on the line covering opposing edge
            // Note: projection perpendicular to the angle bisector will be located an equal distance from intersection point

            double distanceOfVertex = vertex.distance(intersectionPoint);

            // check if the point falls on the edge. I.e. distance from intersection must be between distances of start and end points
            double distOfOpEdgeVertex1 = intersectionPoint.distance(opposingEdge.p0);
            double distOfOpEdgeVertex2 = intersectionPoint.distance(opposingEdge.p1);

            if (distanceOfVertex >= Math.max(distOfOpEdgeVertex1, distOfOpEdgeVertex2) || distanceOfVertex <= Math.min(distOfOpEdgeVertex1, distOfOpEdgeVertex2)) {
                // the projection falls outside of the opposing edge - ignore it
                // This also covers cases when projected point matches the vertex
                return null;
            }

            // determine a point along the opposing edge for which distance from intersection point is equal to that of vertex being projected
            Coordinate furtherPoint = getFurtherEnd(intersectionPoint, opposingEdge);
            LineSegment extendedOpposingEdge = new LineSegment(intersectionPoint, furtherPoint);
            return extendedOpposingEdge.pointAlong(distanceOfVertex / extendedOpposingEdge.getLength());
        } else {
            // In case of parallel lines, we do not have an intersection point
            Coordinate closestPointOnOpposingLine = opposingEdge.project(vertex);       // a projection onto opposingEdge (extending to infinity)
            return isPointOnLineSegmentExcludingEndpoints(closestPointOnOpposingLine, opposingEdge) ? closestPointOnOpposingLine : null;
        }
    }

    /**
     * Returns {@link LineSegment} vertex that is further from given point
     * @param point a point
     * @param lineSegment
     * @return
     */
    private static Coordinate getFurtherEnd(Coordinate point, LineSegment lineSegment) {
        return point.distance(lineSegment.p0) > point.distance(lineSegment.p1) ? lineSegment.p0 : lineSegment.p1;
    }

    /**
     * Checks if the point is located on the given {@link LineSegment} (including endpoints).
     */
    public static boolean isPointOnLineSegment(Coordinate point, LineSegment line) {
        double lengthOfLine = line.getLength();
        double distFromEnd1 = point.distance(line.p0);
        double distFromEnd2 = point.distance(line.p1);

        // this seems to handle robustness errors (due to rounding) better
        if (distFromEnd1 + distFromEnd2 == lengthOfLine) {
            return true;
        }

        // Fallback to what should probably be the robust implementation (TODO: investigate precision issues)
        LineIntersector lineIntersector = new RobustLineIntersector();
        lineIntersector.computeIntersection(point, line.p0, line.p1);
        return lineIntersector.hasIntersection();
    }

    /**
     * Checks if the point is located on the given {@link LineSegment} (excluding endpoints).
     */
    public static boolean isPointOnLineSegmentExcludingEndpoints(Coordinate point, LineSegment line) {
        if (point.equals(line.p0) || point.equals(line.p1)) {
            return false;
        }
        return isPointOnLineSegment(point, line);
    }

    /**
     * Splits a {@link LineString} into {@link LineSegment}s.
     */
    public static List<LineSegment> getLineSegments(LineString lineString) {
        int numPoints = lineString.getNumPoints();
        List<LineSegment> lineSegments = new ArrayList<>(numPoints - 1);

        Coordinate start = lineString.getStartPoint().getCoordinate();
        for (int i = 1; i < numPoints; i++) {
            Coordinate end = lineString.getCoordinateN(i);
            lineSegments.add(new LineSegment(start, end));
            start = end;
        }
        return lineSegments;
    }

    /**
     * Gets the nth {@link LineSegment} of a {@link LineString}.<br>
     * Note: the returned object is safe to modify.
     *
     * @param lineString lineString to extract segment from
     * @param index zero-based index of LineSegment in LineString
     * @return
     */
    public static LineSegment getLineSegment(LineString lineString, int index) {
        return getLineSegment(lineString, index, false);
    }

    /**
     * Gets the nth {@link LineSegment} of a {@link LineString}, possibly reversing it.<br>
     * Note: the returned object is safe to modify.
     *
     * @param lineString lineString to extract segment from
     * @param index zero-based index of LineSegment in LineString
     * @return
     */
    public static LineSegment getLineSegment(LineString lineString, int index, boolean reversed) {
        LineSegment segment = new LineSegment(lineString.getCoordinateN(index), lineString.getCoordinateN(index + 1));
        if (reversed) {
            segment.reverse();
        }
        return segment;
    }

    public static boolean equalWithinDelta(double a, double b) {
        double epsilon = 1e-7;
        return Math.abs(a - b) < epsilon;
    }

    public static class IntersectionCoordinate extends Coordinate {
        private static final long serialVersionUID = 1L;

        private final boolean belongsToOneOfTheEdges;

        public IntersectionCoordinate(double x, double y, LineSegment edgeA, LineSegment edgeB) {
            super(x, y);
            belongsToOneOfTheEdges = isPointOnLineSegmentExcludingEndpoints(this, edgeA) || isPointOnLineSegmentExcludingEndpoints(this, edgeB);
        }
    }

}
