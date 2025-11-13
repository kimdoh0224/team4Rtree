package org.dfpl.dbp.rtree;

import java.util.Iterator;
import java.util.List;

public class Assignment45 {

	public static void main(String[] args) {
		// 4-way R-Tree에 순서대로 삽입될 list
		List<Point> pointList = List.of(new Point(20, 30), new Point(25, 25), new Point(30, 40), new Point(35, 20),
				new Point(40, 35), new Point(15, 45), new Point(45, 15), new Point(28, 32), new Point(30, 150),
				new Point(40, 170), new Point(50, 140), new Point(25, 160), new Point(55, 175), new Point(60, 155),
				new Point(45, 135), new Point(38, 145), new Point(160, 60), new Point(170, 70), new Point(155, 80),
				new Point(180, 55), new Point(175, 90), new Point(165, 95), new Point(150, 75), new Point(185, 85),
				new Point(70, 80), new Point(95, 90), new Point(120, 100), new Point(80, 110), new Point(130, 40),
				new Point(100, 65));

		// Task1: 4-way R-Tree의 생성 (5점)
		// 요건: point가 추가될 때마다 Java Swing 혹은 Java Fx 등의 GUI에 추가된 점과 함께
		// 점에 의해 변형된 계층적 Bounding Box들이 표시되어야 함
		RTree rTree = new RTreeImpl();
		for (Point point : pointList) {
			rTree.add(point);
		}

		// Task234는 Task1이 성공하지 않으면 평가하지 않는다.

		// 과제2: 4-way R-Tree의 탐색 (5점)
		// 요건: 탐색 박스 (0,0,100,100)을 보여주고 내부의 포인트가 드러나게 함 (부분 2.5점)
		// 요건: 검색 범위와 겹치지 않는 영역이 가지치기 되는 점을 부각할 수 있어야 함 (부분 2.5점)
		Iterator<Point> iterator = rTree.search(new Rectangle(new Point(0, 0), new Point(100, 100)));
		while (iterator.hasNext()) {
			Point next = iterator.next();
			System.out.println(next);
//		 	(45,15)
//	     	(35,20)
//	        (25,25)
//	        (20,30)
//	        (28,32)
//	        (15,45)
//	        (30,40)
//	        (40,35)
//	        (70,80)
//	        (95,90)
//	        (100,65)
		}

		// 과제3: 4-way R-Tree의 KNN 검색 (5점)
		// 요건: 탐색된 점들을 보여줄 수 있다 (부분 2.5점)
		// 요건: 탐색의 과정을 단계별로 보여줄 수 있어야 함 (부분 2.5)
		Point source = new Point(75, 85);
		iterator = rTree.nearest(source, 5);
		while (iterator.hasNext()) {
			Point next = iterator.next();
			System.out.println(next + ":" + source.distance(next));			
//				(70,80) : 7.07
//		       	(95,90) : 20.62
//		       	(80,110) : 25.50
//		       	(100,65) : 32.02
//		       	(120,100) : 47.43
		}

		// 과제4: 4-way R-Tree의 노드 제거 (5점)
		// 요건: point가 제거될 때마다 변형된 계층적 Bounding Box들이 표시되어야 함
		List<Point> pointToRemove = List.of(new Point(20, 30), new Point(25, 25), new Point(30, 40), new Point(35, 20),
				new Point(40, 35), new Point(15, 45), new Point(45, 15), new Point(28, 32), new Point(30, 150),
				new Point(40, 170), new Point(50, 140), new Point(25, 160), new Point(55, 175), new Point(60, 155),
				new Point(45, 135), new Point(38, 145), new Point(160, 60), new Point(170, 70), new Point(155, 80),
				new Point(180, 55), new Point(175, 90), new Point(165, 95), new Point(150, 75), new Point(185, 85),
				new Point(70, 80), new Point(95, 90), new Point(120, 100), new Point(80, 110), new Point(130, 40),
				new Point(100, 65));
		for (Point point : pointToRemove) {
			rTree.delete(point);
		}
		System.out.println(rTree.isEmpty());
	}
}
