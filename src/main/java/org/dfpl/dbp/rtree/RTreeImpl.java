package org.dfpl.dbp.rtree;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/*
 * - Task1(ADD): 점 삽입 및 계층적 Bounding Box 확장 시각화
 *      1) 중복 판별
 *      2) 리프 노드 선택(chooseLeaf) - 후보 시각화
 *          * 내부노드면 자식 노드의 MBR 검사 ↓
 *              * enlargement로 각 자식 MBR에 새로운 점을 포함시켰을 때 M면적이 가장 적게 증가하는 자식 선택
 *              * 선택된 MBR 시각화
 *          * 리프 노드면 반환
 *      3) 리프에 점 삽입
 *      4) 트리 조정 및 분할(AdjustTree)
 *          * 삽입된 리프부터 루트까지 상향식으로 MBR 재계산.
 *          * 자식 수 > M(=4)이면 splitNode() - 균등분할 수행.
 *          * 분할 발생 시, 새로운 형제 노드와 부모 갱신 과정 시각화
 *      5) 종료 및 시각화
 *
 * - Task2(SEARCH): 공간 가지치기(spatial pruning) 기반 영역 탐색 시각화
 *      1) 검색 범위 시각화
 *      2) searchRecursive() 수행(dfs 기반 탐색-자식별 교차여부)
 *          * 내부노드:
 *              - 각 자식의 MBR과 검색영역 intersects()로 교차 여부 판단.
 *              - 교차 시: 연두색 배경으로 표시 → 탐색 진행.
 *              - 불교차 시: 분홍색 배경으로 표시 → 가지치기(pruned).
 *          * 리프노드:
 *              - contains(rect, point) 검사.
 *              - 검색 범위 안의 점은 빨간색 점으로 강조.
 *      3) 결과 시각화 및 초기화
 *
 * - Task3(KNN): K-근접 이웃 탐색 시각화 (단순 거리 정렬 기반)
 *      1) 모든 점 수집 + 거리순 정렬
 *      2) 기준 점(초록색) 시각화
 *      3) 인접 점들 순차적 시각화
 *      4) 초기화
 *
 * - Task4(DELETE):
 *      1) deleteRecursive() 수행:
 *          * 접근 중인 노드의 MBR을 highlightRect로 강조 (탐색 경로 시각화).
 *          ① 리프 노드:
 *              - 대상 점을 찾으면 점을 빨간색으로 강조.
 *              - 점 삭제 후 리프의 MBR이 축소되는 과정을 시각화.
 *          ② 내부 노드:
 *              - 각 자식의 MBR을 검사, 점이 포함될 수 있는 자식으로 재귀 이동.
 *              - 삭제 후 비어 있는 자식은 제거(빨간 박스로 강조 후 제거).
 *              - 상향식으로 MBR 갱신 → 축소 반영.
 *      2) 루트 정리:
 *          * 루트가 내부노드이고 자식이 하나뿐이면 높이를 1 줄임.
 *          * 루트가 리프이며 비면 root = null.
 *      3) 결과 시각화
 * GUI 좌표계
 * - 데이터 좌표 (x↑, y↑) 를 화면 좌표 (x→, y↓)로 변환할 때, y축은 아래로 증가하므로 반전
 *   → 화면 y = 패널높이 - (데이터 y * SCALE + margin)
 */

public class RTreeImpl implements RTree {
    // 어떤 시각화 모드인지 - 딜레이에 사용
    private enum Mode {
        ADD, SEARCH, KNN, DELETE, NONE
    }

    private static Mode currentMode = Mode.NONE;
    private static int NODE_COUNTER = 0; // 전역 고유 번호 카운터

    // 기능별 시각화 지연 변수
    private static final int DELAY_ADD = 100;     // 포인트 추가: 빠르게
    private static final int DELAY_SEARCH = 100;  // 탐색: 빠르게
    private static final int DELAY_KNN = 100;     // KNN: 빠르게(원한다면 300 등으로 키워도 됨)
    private static final int DELAY_DELETE = 100; // 삭제: 상대적으로 천천히

    private static final int M = 4; // 최대 차수
    private Node root;
    private static Node instanceRoot;
    private static Map<Rectangle, Integer> rectToId = new HashMap<>();

    // GUI 상태
    private static JFrame frame;
    private static DrawPanel panel;
    private static final int SCALE = 3; // 화면 확대 배율 (데이터 좌표 → 픽셀)
    private static List<Rectangle> allMBR = new ArrayList<>();      // 현재 트리의 모든 MBR
    private static List<Point> allPoints = new ArrayList<>();       // 현재 트리의 모든 점
    private static Rectangle highlightRect = null;                  // 현재 강조(하이라이트) 중인 사각형
    private static List<Point> highlightPoints = new ArrayList<>(); // 현재 강조 중인 점 목록
    private static Rectangle searchHitRect = null;   // 교차 MBR
    private static Rectangle searchPrunedRect = null; // 가지치기 MBR

    // GUI 프레임/패널 초기화: 클래스 로딩 시점에 EDT에서 구성
    static {
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("RTree Visualization");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(900, 900);
            panel = new DrawPanel();
            frame.add(panel);
            frame.setVisible(true);
            panel.repaint(); // 초기 상태를 한 번 강제로 그림
        });
    }

    private void refreshGUI() {
        if (panel == null) return;

        try {
            // Swing EDT에서 상태 수집 및 리페인트를 동기적으로 실행
            SwingUtilities.invokeAndWait(() -> {
                allMBR.clear();
                collectMBRs(root, allMBR);

                if (highlightRect != null)
                    allMBR.add(highlightRect); // 현재 수정 중 노드만 추가 강조

                allPoints.clear();
                collectPoints(root, allPoints);

                panel.repaint();
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            });
        } catch (Exception ignored) {}

        // 모드별 딜레이: 장면 사이 간격을 둬 시뮬레이션처럼 보이게 함
        int delay = switch (currentMode) {
            case ADD -> DELAY_ADD;
            case SEARCH -> DELAY_SEARCH;
            case KNN -> DELAY_KNN;
            case DELETE -> DELAY_DELETE;
            default -> 0;
        };

        try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
    }

    public static void waitForKeyPress() {
        try {
            System.in.read(); // Enter 입력 대기
            while (System.in.available() > 0) System.in.read(); // 버퍼 비우기
        } catch (Exception ignored) {}
    }

    // 시각화용 패널
    private static class DrawPanel extends JPanel {
        private static Point knnSource = null;

        private void drawRectOutline(Graphics g, Rectangle r) {
            int x = (int)(r.getLeftTop().getX() * SCALE + 50);
            int y = getHeight() - (int)(r.getRightBottom().getY() * SCALE + 50);
            int w = (int)((r.getRightBottom().getX() - r.getLeftTop().getX()) * SCALE);
            int h = (int)((r.getRightBottom().getY() - r.getLeftTop().getY()) * SCALE);
            g.drawRect(x, y, w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setStroke(new BasicStroke(1.5f));
            g.setFont(new Font("Arial", Font.PLAIN, 7)); // 좌표용 작은 글씨

            // 배경
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, getWidth(), getHeight());

            // 좌표축 (왼쪽/아래 여백 50 px)
            g.setColor(Color.DARK_GRAY);
            g.drawLine(50, getHeight() - 50, getWidth() - 50, getHeight() - 50); // X축
            g.drawLine(50, getHeight() - 50, 50, 50); // Y축

            // 모든 MBR(회색 윤곽)
            g.setColor(new Color(150, 150, 150, 120));
            for (Rectangle r : allMBR) {
                int x = (int)(r.getLeftTop().getX() * SCALE + 50);
                int y = getHeight() - (int)(r.getRightBottom().getY() * SCALE + 50);
                int w = (int)((r.getRightBottom().getX() - r.getLeftTop().getX()) * SCALE);
                int h = (int)((r.getRightBottom().getY() - r.getLeftTop().getY()) * SCALE);
                g.drawRect(x, y, w, h);

                // MBR 번호 표시 (왼쪽 상단에)
                Integer id = rectToId.get(r);
                if (id != null) {
                    g.setColor(Color.BLACK);
                    g.drawString("N" + id, x + 3, y + 12); // 좌상단 근처에 출력
                    g.setColor(new Color(150, 150, 150, 120)); // 색 원복
                }
            }

            // 일반 점 (파란색)
            g.setColor(Color.BLUE);
            for (Point p : allPoints) {
                int x = (int)(p.getX() * SCALE + 50);
                int y = getHeight() - (int)(p.getY() * SCALE + 50);
                g.fillOval(x - 3, y - 3, 6, 6);
                g.drawString("(" + (int)p.getX() + "," + (int)p.getY() + ")", x + 5, y - 5);
            }

            // 강조 점 (빨간색) - 탐색 결과, 삭제 대상, KNN 결과 등
            g.setColor(Color.RED);
            for (Point p : highlightPoints) {
                int x = (int)(p.getX() * SCALE + 50);
                int y = getHeight() - (int)(p.getY() * SCALE + 50);
                g.fillOval(x - 6, y - 6, 12, 12);
            }

            // KNN 기준점 (초록색)
            if (knnSource != null) {
                g.setColor(Color.GREEN);
                int x = (int)(knnSource.getX() * SCALE + 50);
                int y = getHeight() - (int)(knnSource.getY() * SCALE + 50);
                g.fillOval(x - 6, y - 6, 12, 12);
            }


            // 강조 사각형 (빨간 반투명) - 현재 탐색/삽입 경로 노드 등
            if (highlightRect != null) {
                g.setColor(new Color(255, 0, 0, 80));
                int x = (int)(highlightRect.getLeftTop().getX() * SCALE + 50);
                int y = getHeight() - (int)(highlightRect.getRightBottom().getY() * SCALE + 50);
                int w = (int)((highlightRect.getRightBottom().getX() - highlightRect.getLeftTop().getX()) * SCALE);
                int h = (int)((highlightRect.getRightBottom().getY() - highlightRect.getLeftTop().getY()) * SCALE);
                g.fillRect(x, y, w, h);
            }


            // 1) 가지치기된 MBR (빨간 테두리)
            if (currentMode == Mode.SEARCH && searchPrunedRect != null) {
                g2.setColor(new Color(220, 0, 0)); // 빨간색
                g2.setStroke(new BasicStroke(3f)); // 굵은 테두리
                drawRectOutline(g2, searchPrunedRect);
                g2.setStroke(new BasicStroke(1.5f));
            }

            // 2) 교차한 MBR (초록 테두리)
            if (currentMode == Mode.SEARCH && searchHitRect != null) {
                g2.setColor(new Color(0, 170, 0)); // 초록색
                g2.setStroke(new BasicStroke(3f));
                drawRectOutline(g2, searchHitRect);
                g2.setStroke(new BasicStroke(1.5f));
            }

            // Task2: 탐색 모드일 때 (0,0,100,100) 영역을 항상 연녹색으로 표시
            if (currentMode == Mode.SEARCH) {
                Rectangle searchArea = new Rectangle(new Point(0, 0), new Point(100, 100));

                int x = (int)(searchArea.getLeftTop().getX() * SCALE + 50);
                int y = getHeight() - (int)(searchArea.getRightBottom().getY() * SCALE + 50);
                int w = (int)((searchArea.getRightBottom().getX() - searchArea.getLeftTop().getX()) * SCALE);
                int h = (int)((searchArea.getRightBottom().getY() - searchArea.getLeftTop().getY()) * SCALE);

                // 🔥 테두리만 강조 (굵게)
                Graphics2D g3 = (Graphics2D) g;
                g3.setStroke(new BasicStroke(3.0f));    // 테두리 굵기
                g3.setColor(Color.GREEN.darker());      // 테두리 색
                g3.drawRect(x, y, w, h);

                // 끝나면 기본 굵기로 되돌리기
                g3.setStroke(new BasicStroke(1.5f));
            }

        }
    }

    // R-Tree 노드 구조체
    public static class Node {
        int id;                 // 노드 번호
        boolean isLeaf;          // 리프 여부
        List<Point> points;      // 리프일 때 보관하는 점들
        List<Node> children;     // 내부 노드일 때 자식들
        Rectangle mbr;           // 이 노드가 커버하는 최소 경계 사각형(MBR)
        Node parent;             // 부모 포인터(상향 조정/분할 시 갱신)

        Node(boolean isLeaf) {
            this.isLeaf = isLeaf;
            this.id = ++NODE_COUNTER; // 생성 시 자동으로 번호 부여
            if (isLeaf) points = new ArrayList<>();
            else children = new ArrayList<>();
        }

        // 현재 노드의 점 또는 자식들의 MBR를 바탕으로 자신의 MBR을 재계산
        void updateMBR() {
            if (isLeaf) {
                if (points.isEmpty()) return; // 비어 있으면 유지
                double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
                double maxX = -1, maxY = -1;
                for (Point p : points) {
                    minX = Math.min(minX, p.getX());
                    minY = Math.min(minY, p.getY());
                    maxX = Math.max(maxX, p.getX());
                    maxY = Math.max(maxY, p.getY());
                }
                mbr = new Rectangle(new Point(minX, minY), new Point(maxX, maxY));
            } else {
                if (children.isEmpty()) return;
                double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
                double maxX = -1, maxY = -1;
                for (Node c : children) {
                    Rectangle r = c.mbr;
                    minX = Math.min(minX, r.getLeftTop().getX());
                    minY = Math.min(minY, r.getLeftTop().getY());
                    maxX = Math.max(maxX, r.getRightBottom().getX());
                    maxY = Math.max(maxY, r.getRightBottom().getY());
                }
                mbr = new Rectangle(new Point(minX, minY), new Point(maxX, maxY));
            }
        }
        // 현재 트리의 모든 MBR 출력
        public static void printAllMBRs() {
            System.out.println("==== 현재 트리의 모든 MBR ====");

            // root 기준으로 직접 수집
            allMBR.clear();
            rectToId.clear();

            // root는 static 아니므로 전역 접근 필요
            // 이때 외부에서 RTreeImpl.root를 접근할 수 없으니, static이 아니라면 helper로
            if (instanceRoot != null) collectMBRsStatic(instanceRoot, allMBR);

            if (allMBR.isEmpty()) {
                System.out.println("(no MBRs)");
            } else {
                for (Rectangle r : allMBR) {
                    int id = rectToId.getOrDefault(r, -1);
                    System.out.printf("Node %d -> MBR[(%.1f, %.1f) ~ (%.1f, %.1f)]%n",
                            id,
                            r.getLeftTop().getX(), r.getLeftTop().getY(),
                            r.getRightBottom().getX(), r.getRightBottom().getY());
                }
            }
            System.out.println("=============================");
        }

    }

    // 초기 루트를 리프로 시작하는 R-Tree 생성
    public RTreeImpl() {
        root = new Node(true);
        instanceRoot = root;
    }

    // 유틸
    private void collectMBRs(Node node, List<Rectangle> list) {
        if (node == null || node.mbr == null) return;
        list.add(node.mbr);
        rectToId.put(node.mbr, node.id); // 추가: MBR ↔ Node ID 매핑
        if (!node.isLeaf)
            for (Node c : node.children) collectMBRs(c, list);
    }

    // 루트부터 MBR 정보 수집
    private static void collectMBRsStatic(Node node, List<Rectangle> list) {
        if (node == null || node.mbr == null) return;
        list.add(node.mbr);
        rectToId.put(node.mbr, node.id);
        if (!node.isLeaf)
            for (Node c : node.children) collectMBRsStatic(c, list);
    }

    private void collectPoints(Node node, List<Point> list) {
        if (node == null) return;
        if (node.isLeaf && node.points != null) list.addAll(node.points);
        else if (node.children != null) for (Node c : node.children) collectPoints(c, list);
    }

    private boolean contains(Rectangle r, Point p) {
        return p.getX() >= r.getLeftTop().getX() && p.getX() <= r.getRightBottom().getX()
                && p.getY() >= r.getLeftTop().getY() && p.getY() <= r.getRightBottom().getY();
    }

    private boolean intersects(Rectangle a, Rectangle b) {
        // 두 사각형이 겹치지 않으면 true가 아니게 처리
        return !(a.getRightBottom().getX() < b.getLeftTop().getX() ||
                a.getLeftTop().getX() > b.getRightBottom().getX() ||
                a.getRightBottom().getY() < b.getLeftTop().getY() ||
                a.getLeftTop().getY() > b.getRightBottom().getY());
    }

    /**
     * 삽입 후 조상으로 올라가며 반복 수행
     * 1) MBR 재계산 → 시각화
     * 2) 차수 초과 시 split → 시각화
     */
    private void adjustTreeAnimated(Node n) {
        while (n != null) {
            // 루트는 강조하지 않도록 조건 추가
            boolean isRoot = (n.parent == null);

            // MBR 업데이트
            n.updateMBR();

            // 루트가 아니면 강조
            if (!isRoot) {
                highlightRect = n.mbr;
                refreshGUI();
                highlightRect = null;
            }

            // Overflow → split
            if (n.isLeaf && n.points.size() > M) {
                splitNode(n);
            } else if (!n.isLeaf && n.children.size() > M) {
                splitNode(n);
            }

            // 분할 후 모습
            refreshGUI();

            // 다음 부모로 이동
            if (n.parent != null)
                n = n.parent;
            else
                return;   // 루트 도달
        }

        highlightRect = null;
    }


    /*-----------------ADD----------------*/
    /*
     * - chooseLeaf로 삽입 리프 경로를 단계적으로 강조
     * - 리프에 점 추가 후 MBR 업데이트 (즉시 반영)
     * - adjustTreeAnimated로 조상 MBR/분할 과정을 단계적으로 시각화
     */
    @Override
    public void add(Point point) {
        currentMode = Mode.ADD;

        // 동일 좌표 점 중복 삽입 방지(리프까지 내려가 contains 체크)
        if (exists(root, point)) return;

        // 1) 삽입할 리프 선택: 경로 후보 MBR을 빨간 반투명 강조하며 최소확장 기준으로 내려감
        Node leaf = chooseLeaf(root, point);
        refreshGUI(); // 현재 경로 강조 상태를 한 번 표시

        // 2) 리프에 실제 점 삽입 + MBR 갱신 + 즉시 시각화
        leaf.points.add(point);
        leaf.updateMBR();
        refreshGUI();

        // 3) 조상으로 올라가며 MBR 재계산/분할까지 단계적으로 시각화
        adjustTreeAnimated(leaf);
        highlightRect = null;
        highlightPoints.clear();

        currentMode = Mode.NONE;
    }

    /**
     * 삽입 리프 선택 (최소 면적 증가 기준).
     * - 각 자식 후보를 순회하면서 후보 MBR을 highlightRect로 번갈아 강조
     * - 최종 선택된 자식을 마지막으로 다시 강조
     * - 재귀적으로 리프까지 진행
     */
    private Node chooseLeaf(Node n, Point p) {
        if (n.isLeaf) return n;

        Node best = null;
        double bestArea = Double.MAX_VALUE;

        for (Node c : n.children) {
            double enlarge = enlargement(c.mbr, p);
            if (enlarge < bestArea) {
                bestArea = enlarge;
                best = c;
            }
        }

        // 최종 선택된 자식을 한 번 더 강조해 '선택됨'을 명시적으로 보여줌
        highlightRect = best.mbr;
        refreshGUI();
        highlightRect = null;

        return chooseLeaf(best, p);
    }

    // 점 p를 포함시키기 위해 기존 MBR r의 면적 증가량 계산
    private double enlargement(Rectangle r, Point p) {
        double minX = Math.min(r.getLeftTop().getX(), p.getX());
        double minY = Math.min(r.getLeftTop().getY(), p.getY());
        double maxX = Math.max(r.getRightBottom().getX(), p.getX());
        double maxY = Math.max(r.getRightBottom().getY(), p.getY());
        double old = (r.getRightBottom().getX() - r.getLeftTop().getX()) * (r.getRightBottom().getY() - r.getLeftTop().getY());
        double neu = (maxX - minX) * (maxY - minY);
        return neu - old;
    }

    /**
     * 노드 분할(split):
     * - 자식 수가 M(=4)를 초과하면 호출됨.
     * - 현재 노드를 둘로 나누고, 부모에 새 sibling을 추가함.
     * - 루트 분할이 발생하면 새로운 루트를 자동 생성하여 트리 높이를 증가시킴.
     * - n과 sibling의 MBR을 재계산한 후 시각화를 갱신함.
     *
     * 절차:
     *   1) n이 루트라면 먼저 새로운 루트 생성 (트리 높이 증가)
     *   2) n을 절반(split)하여 새로운 sibling 노드 생성
     *   3) 부모(parent)에 sibling을 붙임
     *   4) 부모 MBR 갱신
     *   5) 과정 중 refreshGUI()로 단계별 시각화
     */
    private void splitNode(Node n) {
        // 현재 노드가 root일 때 새 루트 생성
        if (n == root && n.parent == null) {
            Node newRoot = new Node(false);
            newRoot.children = new ArrayList<>();
            newRoot.children.add(n);
            n.parent = newRoot;
            root = newRoot;
            instanceRoot = root;
            refreshGUI();
        }

        Node sibling = new Node(n.isLeaf);
        if (n.isLeaf) {     // point 절반 분할
            int half = n.points.size() / 2;
            sibling.points.addAll(n.points.subList(half, n.points.size()));
            n.points = new ArrayList<>(n.points.subList(0, half));
        } else {    // children 절반 분할
            int half = n.children.size() / 2;
            sibling.children.addAll(n.children.subList(half, n.children.size()));
            n.children = new ArrayList<>(n.children.subList(0, half));
            // 분리된 자식들 parent 업데이트
            for (Node c : sibling.children)
                c.parent = sibling;
        }

        // 분할된 n, sibling MBR 재계산
        n.updateMBR();
        sibling.updateMBR();
        refreshGUI();

        Node parent = n.parent;
        if (parent == null) {
            // parent가 null일 땐 여기서 새 루트 생성
            parent = new Node(false);
            parent.children.add(n);
            root = parent;
            instanceRoot = root;
            n.parent = parent;
        }

        parent.children.add(sibling);
        sibling.parent = parent;
        parent.updateMBR();

        refreshGUI();
        highlightRect = null;
    }

    /* 동일 좌표의 점이 유무 검사 (리프까지) */
    private boolean exists(Node node, Point p) {
        if (node.isLeaf) {
            for (Point q : node.points)
                if (q.getX() == p.getX() && q.getY() == p.getY()) return true;
        } else {
            for (Node c : node.children)
                if (contains(c.mbr, p) && exists(c, p)) return true;
        }
        return false;
    }

    /*-----------------Search----------------*/
    /*
     * - searchRecursive에서 노드 MBR과의 교차 여부에 따라
     *   * 겹침 : 연두색 배경(탐색 진행)
     *   * 안겹침 : 분홍색 배경(가지치기) - pruning 강조
     * - 조건을 만족하는 점은 하나씩 빨간 점으로 추가/강조하며 단계적으로 표시
     */
    @Override
    public Iterator<Point> search(Rectangle rectangle) {
        currentMode = Mode.SEARCH;

        List<Point> result = new ArrayList<>();
        highlightRect = rectangle;   // 검색 영역 강조
        highlightPoints.clear();     // 기존 강조점 초기화

        // 실제 검색(DFS)
        searchRecursive(root, rectangle, result);

        // 최종 결과를 한눈에 보이도록 강조점 유지
        highlightPoints.addAll(result);
        refreshGUI();

        // 원복
        highlightRect = null;
        currentMode = Mode.NONE;

        return result.iterator();
    }

    /**
     * 검색 재귀:
     * - 리프 : 점을 검사하면서 조건에 맞는 점들을 하나씩 강조/추가
     * - 내부노드: 자식별로 MBR 교차 여부를 확인:
     *      * 교차: panel 배경을 연두색 → 탐색 진행 장면
     *      * 불교차: panel 배경을 분홍색 → 가지치기 장면
     */
    private void searchRecursive(Node n, Rectangle r, List<Point> out) {
        if (n == null) return;

        // 🔥 리프 노드인 경우 - 점 검사
        if (n.isLeaf) {
            for (Point p : n.points) {
                if (contains(r, p)) {
                    highlightPoints.add(p);
                    refreshGUI();
                    out.add(p);
                    waitForKeyPress();     // 🔥 여기가 정상 호출됨
                }
            }
            return;
        }

        // 🔥 내부 노드: 자식들 검사
        for (Node c : n.children) {

            if (intersects(c.mbr, r)) {
                // 교차된 MBR 표시
                searchHitRect = c.mbr;
                searchPrunedRect = null;
            } else {
                // 가지치기된 MBR 표시
                searchHitRect = null;
                searchPrunedRect = c.mbr;
            }

            refreshGUI();
            waitForKeyPress();            // 🔥 여기서 한 단계씩 멈춰 보여줌

            if (intersects(c.mbr, r)) {
                // 🔥 교차된 경우에만 재귀 진입
                searchRecursive(c, r, out);
            }
        }

        // 한 단계 종료 후 초기화
        searchHitRect = null;
        searchPrunedRect = null;
    }

    /*-----------------KNN----------------*/
    @Override
    public Iterator<Point> nearest(Point source, int k) {
        currentMode = Mode.KNN;

        // 모든 점 수집 후 거리순 정렬
        List<Point> all = new ArrayList<>();
        collectPoints(root, all);
        all.sort(Comparator.comparingDouble(source::distance));

        // 기준점 강조
        highlightPoints.clear();
        DrawPanel.knnSource = source;
        refreshGUI();

        // k개를 하나씩 빨간 점으로 추가하여 “탐색의 진행”을 보여줌
        for (int i = 0; i < k && i < all.size(); i++) {
            highlightPoints.add(all.get(i));
            refreshGUI();
            waitForKeyPress();
        }

        // 끝난 후 강조 해제
        highlightPoints.clear();
        DrawPanel.knnSource = null;
        currentMode = Mode.NONE;

        // 결과 반환
        return all.subList(0, Math.min(k, all.size())).iterator();
    }

    /*-----------------DELETE----------------*/
    /**
     * - deleteRecursive로 내려가며 삭제 대상 점을 빨간 점으로 잠깐 강조 후 제거
     * - 제거 후 리프/내부 노드가 비면 그 노드를 부모에서 제거
     * - 조상으로 올라가며 MBR을 축소 갱신
     */
    @Override
    public void delete(Point point) {
        currentMode = Mode.DELETE;

        // 실제 삭제 시도
        deleteRecursive(root, point);

        // 루트 정리: 루트가 내부노드인데 자식 하나만 남았으면 높이를 1 줄임,
        // 루트가 리프이고 비었으면 트리를 비움
        if (!root.isLeaf && root.children.size() == 1) {
            root = root.children.get(0);
            root.parent = null;
        }
        if (root.isLeaf && root.points.isEmpty()) root = null;

        refreshGUI();
        currentMode = Mode.NONE;
    }

    /**
     * 삭제 재귀:
     * - 각 노드에 진입할 때 highlightRect로 해당 노드 MBR 강조(경로 시각화)
     * - 리프라면 대상 점을 찾고 빨간 점으로 강조 → remove → MBR 갱신 → 장면 표시
     * - 내부노드라면, p를 포함할 수 있는 자식만 재귀. 삭제 성공 후:
     *   * 자식이 비면 해당 자식을 제거
     *   * 현재 노드의 MBR 업데이트
     */
    private boolean deleteRecursive(Node n, Point p) {
        if (n == null) return false;

        // 현재 방문 노드의 MBR 강조
        highlightRect = n.mbr;
        refreshGUI();
        try { Thread.sleep(150); } catch (InterruptedException ignored) {}

        if (n.isLeaf) {
            // 리프에서 직접 점을 찾아 제거
            Iterator<Point> it = n.points.iterator();
            while (it.hasNext()) {
                Point q = it.next();
                if (q.getX() == p.getX() && q.getY() == p.getY()) {
                    // 삭제 대상 점을 잠깐 크게 빨간 점으로 강조
                    highlightPoints.clear();
                    highlightPoints.add(q);
                    refreshGUI();

                    // 실제 제거 + MBR 갱신
                    it.remove();
                    n.updateMBR();
                    refreshGUI();
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                    return true;
                }
            }
            return false;
        } else {
            // 내부노드: p가 포함될 수 있는 자식만 탐색
            for (Node c : n.children) {
                if (contains(c.mbr, p)) {
                    if (deleteRecursive(c, p)) {
                        // 자식이 비었으면 제거
                        if ((c.isLeaf && c.points.isEmpty()) || (!c.isLeaf && c.children.isEmpty())) {
                            highlightRect = c.mbr; // 제거 직전 자식 강조
                            refreshGUI();
                            try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                            n.children.remove(c);
                        }
                        // 현재 노드의 MBR 축소 반영
                        n.updateMBR();
                        refreshGUI();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        return root == null || (root.isLeaf && root.points.isEmpty());
    }
}
