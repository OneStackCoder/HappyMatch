package com.happymatch.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.happymatch.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 棋盘视图：负责棋盘的绘制、点击选块，以及“最多两个拐角”的连通判定。
 *
 * <p>游戏流程（计时、道具、音效）由子类 {@link GameView} 负责，本类保持业务无关。</p>
 */
public class BoardView extends View {

    /** 棋盘行数 */
    public static final int ROWS = 8;
    /** 棋盘列数 */
    public static final int COLS = 8;
    /** 水果种类数 */
    public static final int ICON_COUNT = 16;

    /** 空格子 */
    protected static final int BLANK = -1;

    /** 连线展示时间（毫秒） */
    private static final long LINK_SHOW_DURATION = 180L;
    /** 图标缓存尺寸 */
    private static final int ICON_SOURCE_SIZE = 128;

    /** 棋盘数据，board[y][x] 存放水果种类，BLANK 表示空 */
    protected final int[][] board = new int[ROWS][COLS];
    /** 已选中的格子，最多两个 */
    protected final List<Point> selected = new ArrayList<>(2);

    private final Bitmap[] iconBitmaps = new Bitmap[ICON_COUNT];
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint linkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 离屏画布，避免每次 onDraw 重绘所有图标 */
    private Bitmap buffer;
    private int cellSize;
    private int gridLeft;
    private int gridTop;

    private boolean interactive = true;
    private boolean resolving;

    private LinkType linkType = LinkType.NONE;
    private Point flex1;
    private Point flex2;

    /** 等待删除的两个格子，锁定后不受选中状态变化影响 */
    private Point pendingA;
    private Point pendingB;

    private final Runnable removeRunnable = new Runnable() {
        @Override
        public void run() {
            removeMatchedTiles();
        }
    };

    private enum LinkType {
        NONE, LINE, ONE_CORNER, TWO_CORNER
    }

    public BoardView(Context context) {
        this(context, null);
    }

    public BoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                board[y][x] = BLANK;
            }
        }
        loadIcons();
        initPaints();
    }

    private void loadIcons() {
        int[] resIds = {
                R.drawable.fruit_00, R.drawable.fruit_01, R.drawable.fruit_02, R.drawable.fruit_03,
                R.drawable.fruit_04, R.drawable.fruit_05, R.drawable.fruit_06, R.drawable.fruit_07,
                R.drawable.fruit_08, R.drawable.fruit_09, R.drawable.fruit_10, R.drawable.fruit_11,
                R.drawable.fruit_12, R.drawable.fruit_13, R.drawable.fruit_14, R.drawable.fruit_15
        };
        for (int i = 0; i < ICON_COUNT; i++) {
            Bitmap bitmap = Bitmap.createBitmap(ICON_SOURCE_SIZE, ICON_SOURCE_SIZE,
                    Bitmap.Config.ARGB_8888);
            Drawable drawable = getContext().getDrawable(resIds[i]);
            if (drawable != null) {
                drawable.setBounds(0, 0, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE);
                drawable.draw(new Canvas(bitmap));
            }
            iconBitmaps[i] = bitmap;
        }
    }

    private void initPaints() {
        cellPaint.setColor(0x33FFFFFF);
        cellPaint.setStyle(Paint.Style.FILL);
        linkPaint.setColor(Color.rgb(52, 196, 227));
        linkPaint.setStyle(Paint.Style.STROKE);
        linkPaint.setStrokeCap(Paint.Cap.ROUND);
        linkPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    // ----------------------------------------------------------------------------------
    // 尺寸与绘制
    // ----------------------------------------------------------------------------------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(width, height);
        if (size <= 0) {
            size = Math.max(width, height);
        }
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cellSize = Math.min(w, h) / (COLS + 2);
        gridLeft = (w - cellSize * COLS) / 2;
        gridTop = (h - cellSize * ROWS) / 2;
        if (buffer == null || buffer.getWidth() != w || buffer.getHeight() != h) {
            buffer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }
        drawBoard();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (buffer != null) {
            canvas.drawBitmap(buffer, 0, 0, null);
        }
    }

    /** 重绘整个棋盘到离屏 Bitmap */
    protected void drawBoard() {
        if (buffer == null) {
            return;
        }
        Canvas canvas = new Canvas(buffer);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        // 先画底板，再画未选中的图标，最后画选中图标，保证放大的图标在最上层
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (board[y][x] != BLANK) {
                    drawCellBackground(canvas, x, y);
                }
            }
        }
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (board[y][x] != BLANK && !isSelected(x, y)) {
                    drawCellIcon(canvas, x, y, false);
                }
            }
        }
        for (Point p : selected) {
            if (isInsideBoard(p.x, p.y) && board[p.y][p.x] != BLANK) {
                drawCellIcon(canvas, p.x, p.y, true);
            }
        }
    }

    private void drawCellBackground(Canvas canvas, int x, int y) {
        Point p = cellToScreen(x, y);
        float padding = Math.max(1, cellSize / 32f);
        RectF rect = new RectF(p.x + padding, p.y + padding,
                p.x + cellSize - padding, p.y + cellSize - padding);
        canvas.drawRoundRect(rect, cellSize / 7f, cellSize / 7f, cellPaint);
    }

    private void drawCellIcon(Canvas canvas, int x, int y, boolean selected) {
        Point p = cellToScreen(x, y);
        int value = board[y][x];
        Rect dst;
        if (selected) {
            int grow = cellSize / 10;
            dst = new Rect(p.x - grow, p.y - grow,
                    p.x + cellSize + grow, p.y + cellSize + grow);
        } else {
            int inset = cellSize / 24;
            dst = new Rect(p.x + inset, p.y + inset,
                    p.x + cellSize - inset, p.y + cellSize - inset);
        }
        canvas.drawBitmap(iconBitmaps[value], null, dst, iconPaint);
    }

    /** 画出两个格子之间的连线 */
    protected void drawLinkLine(Point a, Point b) {
        if (buffer == null || linkType == LinkType.NONE) {
            return;
        }
        Canvas canvas = new Canvas(buffer);
        linkPaint.setStrokeWidth(Math.max(4f, cellSize / 14f));

        Point start = cellCenter(a.x, a.y);
        Point end = cellCenter(b.x, b.y);

        switch (linkType) {
            case LINE:
                canvas.drawLine(start.x, start.y, end.x, end.y, linkPaint);
                break;
            case ONE_CORNER:
                if (flex1 != null) {
                    Point corner = cellCenter(flex1.x, flex1.y);
                    canvas.drawLine(start.x, start.y, corner.x, corner.y, linkPaint);
                    canvas.drawLine(corner.x, corner.y, end.x, end.y, linkPaint);
                }
                break;
            case TWO_CORNER:
                if (flex1 != null && flex2 != null) {
                    Point c1 = cellCenter(flex1.x, flex1.y);
                    Point c2 = cellCenter(flex2.x, flex2.y);
                    canvas.drawLine(start.x, start.y, c1.x, c1.y, linkPaint);
                    canvas.drawLine(c1.x, c1.y, c2.x, c2.y, linkPaint);
                    canvas.drawLine(c2.x, c2.y, end.x, end.y, linkPaint);
                }
                break;
            default:
                break;
        }
    }

    // ----------------------------------------------------------------------------------
    // 棋盘数据操作（供子类调用）
    // ----------------------------------------------------------------------------------

    /** 生成一局新棋盘：每种水果数量成对，且保证初始至少存在一组可消除的格子 */
    public void newBoard() {
        cancelPendingMatch();
        int total = ROWS * COLS;
        int pairCount = total / 2;
        List<Integer> tiles = new ArrayList<>(total);
        for (int i = 0; i < pairCount; i++) {
            int type = i % ICON_COUNT;
            tiles.add(type);
            tiles.add(type);
        }

        int guard = 0;
        do {
            Collections.shuffle(tiles, random);
            for (int y = 0; y < ROWS; y++) {
                for (int x = 0; x < COLS; x++) {
                    board[y][x] = tiles.get(y * COLS + x);
                }
            }
            guard++;
        } while (!hasMove() && guard < 100);

        selected.clear();
        resetLinkState();
        drawBoard();
        invalidate();
    }

    /** 打乱剩余格子中的水果，空格保持不动 */
    public void shuffleBoard() {
        cancelPendingMatch();
        List<Point> cells = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (board[y][x] != BLANK) {
                    cells.add(new Point(x, y));
                    values.add(board[y][x]);
                }
            }
        }
        if (values.size() < 2) {
            return;
        }

        int guard = 0;
        do {
            Collections.shuffle(values, random);
            for (int i = 0; i < cells.size(); i++) {
                Point p = cells.get(i);
                board[p.y][p.x] = values.get(i);
            }
            guard++;
        } while (!hasMove() && guard < 200);

        selected.clear();
        resetLinkState();
        drawBoard();
        invalidate();
    }

    /** 查找一组可消除的格子，找不到返回 null */
    public Point[] findHint() {
        for (int y1 = 0; y1 < ROWS; y1++) {
            for (int x1 = 0; x1 < COLS; x1++) {
                int value = board[y1][x1];
                if (value == BLANK) {
                    continue;
                }
                for (int y2 = y1; y2 < ROWS; y2++) {
                    int startX = (y2 == y1) ? x1 + 1 : 0;
                    for (int x2 = startX; x2 < COLS; x2++) {
                        if (board[y2][x2] == value) {
                            Point a = new Point(x1, y1);
                            Point b = new Point(x2, y2);
                            if (canLink(a, b)) {
                                return new Point[]{a, b};
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 当前是否还存在可消除的组合 */
    public boolean hasMove() {
        return findHint() != null;
    }

    /** 是否已经清空棋盘 */
    public boolean isCleared() {
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (board[y][x] != BLANK) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 高亮一组提示格子 */
    public void showHint(Point a, Point b) {
        if (resolving) {
            return;
        }
        selected.clear();
        selected.add(a);
        selected.add(b);
        drawBoard();
        if (canLink(a, b)) {
            drawLinkLine(a, b);
        }
        invalidate();
    }

    /** 清除提示高亮 */
    public void clearHint() {
        selected.clear();
        resetLinkState();
        drawBoard();
        invalidate();
    }

    public void clearSelection() {
        selected.clear();
        drawBoard();
        invalidate();
    }

    /** 是否允许响应点击 */
    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    /** 是否正在进行消除动画，此期间应忽略棋盘与道具操作 */
    public boolean isResolving() {
        return resolving;
    }

    // ----------------------------------------------------------------------------------
    // 触摸交互
    // ----------------------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (!interactive || resolving) {
            return true;
        }
        Point cell = pointToCell((int) event.getX(), (int) event.getY());
        if (cell == null || board[cell.y][cell.x] == BLANK) {
            return true;
        }
        handleCell(cell);
        return true;
    }

    private void handleCell(Point cell) {
        if (selected.isEmpty()) {
            selectOnly(cell);
            onTileSelected(cell);
            return;
        }

        Point first = selected.get(0);
        if (first.equals(cell)) {
            selected.clear();
            drawBoard();
            invalidate();
            onSelectionCanceled();
            return;
        }

        if (canLink(first, cell)) {
            selected.add(cell);
            drawBoard();
            drawLinkLine(first, cell);
            invalidate();

            resolving = true;
            pendingA = first;
            pendingB = cell;
            onLinkCreated(first, cell);
            handler.postDelayed(removeRunnable, LINK_SHOW_DURATION);
        } else {
            selectOnly(cell);
            onLinkFailed(first, cell);
        }
    }

    private void selectOnly(Point cell) {
        selected.clear();
        selected.add(cell);
        drawBoard();
        invalidate();
    }

    private void removeMatchedTiles() {
        if (pendingA == null || pendingB == null) {
            resolving = false;
            return;
        }
        Point a = pendingA;
        Point b = pendingB;
        pendingA = null;
        pendingB = null;
        board[a.y][a.x] = BLANK;
        board[b.y][b.x] = BLANK;

        selected.clear();
        resetLinkState();
        resolving = false;
        drawBoard();
        invalidate();

        onMatchRemoved(a, b);
    }

    /** 取消尚未执行的消除动画 */
    private void cancelPendingMatch() {
        handler.removeCallbacks(removeRunnable);
        pendingA = null;
        pendingB = null;
        resolving = false;
    }

    private void resetLinkState() {
        linkType = LinkType.NONE;
        flex1 = null;
        flex2 = null;
    }

    // ----------------------------------------------------------------------------------
    // 连通判定（允许走棋盘外一圈）
    // ----------------------------------------------------------------------------------

    /**
     * 判断两个格子是否可以消除。
     * 判定结果会记录在 {@link #linkType}、{@link #flex1}、{@link #flex2} 中，供画线使用。
     */
    protected boolean canLink(Point a, Point b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        int value = board[a.y][a.x];
        if (value == BLANK || value != board[b.y][b.x]) {
            return false;
        }

        // 直线相连
        if (straightClear(a.x, a.y, b.x, b.y)) {
            linkType = LinkType.LINE;
            flex1 = null;
            flex2 = null;
            return true;
        }

        // 一个拐角：竖直再水平
        Point corner1 = new Point(a.x, b.y);
        if (isBlankCell(corner1.x, corner1.y)
                && straightClear(a.x, a.y, corner1.x, corner1.y)
                && straightClear(corner1.x, corner1.y, b.x, b.y)) {
            linkType = LinkType.ONE_CORNER;
            flex1 = corner1;
            flex2 = null;
            return true;
        }

        // 一个拐角：水平再竖直
        Point corner2 = new Point(b.x, a.y);
        if (isBlankCell(corner2.x, corner2.y)
                && straightClear(a.x, a.y, corner2.x, corner2.y)
                && straightClear(corner2.x, corner2.y, b.x, b.y)) {
            linkType = LinkType.ONE_CORNER;
            flex1 = corner2;
            flex2 = null;
            return true;
        }

        return twoCornerLink(a, b);
    }

    /** 两个拐角：先走竖直通道，再走水平通道 */
    private boolean twoCornerLink(Point a, Point b) {
        for (int x = -1; x <= COLS; x++) {
            if (!isBlankCell(x, a.y) || !isBlankCell(x, b.y)) {
                continue;
            }
            if (straightClear(a.x, a.y, x, a.y)
                    && straightClear(x, a.y, x, b.y)
                    && straightClear(x, b.y, b.x, b.y)) {
                linkType = LinkType.TWO_CORNER;
                flex1 = new Point(x, a.y);
                flex2 = new Point(x, b.y);
                return true;
            }
        }
        for (int y = -1; y <= ROWS; y++) {
            if (!isBlankCell(a.x, y) || !isBlankCell(b.x, y)) {
                continue;
            }
            if (straightClear(a.x, a.y, a.x, y)
                    && straightClear(a.x, y, b.x, y)
                    && straightClear(b.x, y, b.x, b.y)) {
                linkType = LinkType.TWO_CORNER;
                flex1 = new Point(a.x, y);
                flex2 = new Point(b.x, y);
                return true;
            }
        }
        return false;
    }

    /** 两点同行/同列且中间全为空格（不含两端） */
    private boolean straightClear(int x1, int y1, int x2, int y2) {
        if (x1 == x2) {
            int from = Math.min(y1, y2) + 1;
            int to = Math.max(y1, y2) - 1;
            for (int y = from; y <= to; y++) {
                if (!isBlankCell(x1, y)) {
                    return false;
                }
            }
            return true;
        }
        if (y1 == y2) {
            int from = Math.min(x1, x2) + 1;
            int to = Math.max(x1, x2) - 1;
            for (int x = from; x <= to; x++) {
                if (!isBlankCell(x, y1)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /** 空格判定；棋盘外一圈视为可通行 */
    protected boolean isBlankCell(int x, int y) {
        if (!isInsideBoard(x, y)) {
            return true;
        }
        return board[y][x] == BLANK;
    }

    private boolean isInsideBoard(int x, int y) {
        return x >= 0 && x < COLS && y >= 0 && y < ROWS;
    }

    private boolean isSelected(int x, int y) {
        for (Point p : selected) {
            if (p.x == x && p.y == y) {
                return true;
            }
        }
        return false;
    }

    private Point cellToScreen(int x, int y) {
        return new Point(gridLeft + x * cellSize, gridTop + y * cellSize);
    }

    private Point cellCenter(int x, int y) {
        return new Point(gridLeft + x * cellSize + cellSize / 2,
                gridTop + y * cellSize + cellSize / 2);
    }

    private Point pointToCell(int px, int py) {
        if (cellSize <= 0 || px < gridLeft || py < gridTop) {
            return null;
        }
        int x = (px - gridLeft) / cellSize;
        int y = (py - gridTop) / cellSize;
        if (!isInsideBoard(x, y)) {
            return null;
        }
        return new Point(x, y);
    }

    // ----------------------------------------------------------------------------------
    // 交互回调，供子类重写
    // ----------------------------------------------------------------------------------

    protected void onTileSelected(Point cell) {
    }

    protected void onSelectionCanceled() {
    }

    protected void onLinkCreated(Point a, Point b) {
    }

    protected void onLinkFailed(Point first, Point second) {
    }

    protected void onMatchRemoved(Point a, Point b) {
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
