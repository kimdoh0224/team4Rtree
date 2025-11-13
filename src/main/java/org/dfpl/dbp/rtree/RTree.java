package org.dfpl.dbp.rtree;

import java.util.Iterator;

public interface RTree {

	/**
	 * RTree에 Point를 추가합니다. 같은 x, y 값을 갖는 Point는 추가하지 않습니다. 
	 * @param point
	 */
	public void add(Point point);
	
	/**
	 * RTree내에서 rectangle 범위내에 있는 Point들을 iterator로 반환합니다. (경계 포함) 
	 * 
	 * @param rectangle
	 * @return iterator<Point>
	 */
	public Iterator<Point> search(Rectangle rectangle);
	

	/**
	 * RTree에서 source로 부터 가까운 최대 maxCount개의 Point들을 가까운 순서대로 반환합니다. 
	 * 
	 * @param source
	 * @param maxCount
	 * @return iterator<Point>
	 */
	public Iterator<Point> nearest(Point source, int maxCount);
	
	/**
	 * Point가 R-Tree에 존재한다면 지운다. 
	 * 
	 * @param point
	 */
	public void delete(Point point);
	
	/**
	 * R-Tree에 Point가 하나 이상 있다면 true를 반환한다. 
	 * 
	 * @return
	 */
	public boolean isEmpty();
}
