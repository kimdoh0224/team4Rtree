package org.dfpl.dbp.rtree;

public class Rectangle {

	// 좌상단 포인트와 우하단 포인트로 표현
	private Point leftTop;
	private Point rightBottom;

	public Rectangle(Point leftTop, Point rightBottom) {
		super();
		this.leftTop = leftTop;
		this.rightBottom = rightBottom;
	}

	public Point getLeftTop() {
		return leftTop;
	}

	public void setLeftTop(Point leftTop) {
		this.leftTop = leftTop;
	}

	public Point getRightBottom() {
		return rightBottom;
	}

	public void setRightBottom(Point rightBottom) {
		this.rightBottom = rightBottom;
	}

	@Override
	public String toString() {
		return "Rectangle [leftTop=(" + leftTop.getX() + "," + leftTop.getY() + "), rightBottom=(" + rightBottom.getX()
				+ "," + rightBottom.getY() + ")]";
	}
}
