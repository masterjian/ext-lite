package extlite.mvc;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import db.DBUtils;
import org.apache.commons.beanutils.BeanUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 一个极度精简版的MVC框架
 */
public abstract class BaseController extends HttpServlet {

    private static String VIEW_BASE_PATH = "/WEB-INF/views/";
    private static String DRIVER = "com.mysql.jdbc.Driver";
    private static String URL = "jdbc:mysql://localhost:3306/demo?useSSL=false";
    private static String USER = "root";
    private static String PASSWORD = "root";
    private static String DB = "demo";
    private static String HOST = "localhost";
    private static String PORT = "3306";

    static {
        try {

            // 获取配置文件
            InputStream inputStream = DBUtils.class.getClassLoader().getResourceAsStream("db.properties");
            Properties properties = new Properties();
            properties.load(inputStream);

            // 获取配置文件中的参数
            URL = properties.getProperty("url");
            USER = properties.getProperty("user");
            PASSWORD = properties.getProperty("password");
            DRIVER = properties.getProperty("driver");
            DB = properties.getProperty("db");
            HOST = properties.getProperty("host");
            PORT = properties.getProperty("port");

            inputStream.close();
            // 初始化数据库连接源
            URL = URL.replace("{db}", DB)
                    .replace("{port}", PORT)
                    .replace("{host}", HOST);

            Class.forName(DRIVER);

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 保存当前请求的HttpServletRequest对象
    HttpServletRequest req;
    // 保存当前请求的HttpServletResponse对象
    HttpServletResponse resp;

    // 保存当前请求的数据库连接对象（Ormlite）
    ConnectionSource connectionSource;

    private static final long serialVersionUID = 54250L;

    // 当前控制器的名称
    private String controllerName;

    //当前操作方法的名称
    private String action;

    /**
     * 设置当前控制器的名称
     *
     * @param controllerName
     */
    protected void setControllerName(String controllerName) {
        this.controllerName = controllerName;
    }

    /**
     * 获取当前控制器的名
     *
     * @return
     */
    protected String getControllerName() {
        return controllerName;
    }

    /**
     * 获取当前操作方法的名称
     *
     * @return
     */
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    /**
     * 当前控制初始化的操作方法，在子类中重写，整个生命周期只执行一次，与Servlet的init方法相同生命周期
     */
    public abstract void init();

    /**
     * Servlet的init方法,在此方法中初始化数据库连接源
     */
    @Override
    public final void init(ServletConfig config) throws ServletException {
        super.init(config);

        init();
        // 创建数据库连接
        try {
            connectionSource = new JdbcConnectionSource(
                    URL,
                    USER,
                    PASSWORD);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 主动生成指定实体类的DAO对象
     *
     * @param clazz 实体类的Class对象
     * @param <T>   实体类的类型
     * @param <T1>  实体类的主键的数据类型
     * @return 实体类的DAO对象，如果没有找到，返回null
     */
    protected <T extends BaseModel, T1> Dao<T, T1> myDAO(Class<T> clazz) {
        try {
            return DaoManager.createDao(connectionSource, clazz);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 从HttpServletRequest对象中获取参数，并自动匹配参数名称和参数值，并返回一个实体类对象
     * <p>
     * 方法没有返回值，通过填充形参的方式返回
     * <p>
     * <b> TBD：</b>暂未实现参数值是数组的情况
     *
     * @param bean 实体类对象，用于自动匹配参数名称和参数值，主动填充形参的值
     * @param <T>
     */
    protected <T> void myBean(T bean) {
        try {
            // 使用commons-beanutils包，自动匹配参数名称和参数值
            BeanUtils.populate(bean, req.getParameterMap());
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 利用此方法，将Servlet中的数据传递给模板引擎，并返回模板引擎生成的页面，作用域是Request
     *
     * @param param 参数名称
     * @param value 参数值的值
     */
    protected void beforeForward(String param, Object value) {
        req.setAttribute(param, value);
    }

    /**
     * 每个操作方法的默认跳转，此方法执行后，会主动调用相同控制器目录下的相同操作方法的jsp文件
     */
    protected void forward() {
        this.req.setAttribute(getControllerName() + ".forward", true);
        this.req.setAttribute(getControllerName() + ".targetView", this.getAction());
    }

    /**
     * 如果希望跳转到当前控制器的其它视图
     *
     * @param targetView 相同控制器下的其他视图名，<p><b>注意：</b>不包含扩展名</p>
     */
    protected void forward(String targetView) {
        this.req.setAttribute(getControllerName() + ".forward", true);
        this.req.setAttribute(getControllerName() + ".targetView", targetView);
    }

    private void _forward(String targetView) throws ServletException, IOException {
        this.req.getRequestDispatcher(VIEW_BASE_PATH + getControllerName() + "/" + targetView + ".jsp").forward(this.req, this.resp);
    }

    private void _forward() throws ServletException, IOException {
        _forward(VIEW_BASE_PATH + getControllerName() + "/" + getAction() + ".jsp");
    }

    private void redirect(String view) throws ServletException, IOException {
        _forward(VIEW_BASE_PATH + getControllerName() + "/" + view + ".jsp");
    }

    /**
     * 从<b>request</b>的get方法的查询字符串或者post方法的表单获取值
     *
     * @param key 查询字符串或者表单中的name属性
     * @return 返回值需要自己根据实际进行转换
     */
    protected Object param(String key) {
        return req.getParameter(key);
    }

    /**
     * 从<b>request</b>的属性中获取值
     *
     * @param key 属性中的key
     * @return 返回值需要自己根据实际进行转换
     */
    protected Object attr(String key) {
        return req.getAttribute(key);
    }

    /**
     * 用于<b>列表-index</b>页面的显示
     *
     * @throws ServletException
     * @throws IOException
     * @throws SQLException
     */
    protected abstract void index() throws ServletException, IOException, SQLException;

    /**
     * 用于<b>添加-add</b>页面的初始化显示，一般与create操作方法配合使用
     *
     * @throws ServletException
     * @throws IOException
     * @throws SQLException
     */
    protected abstract void add() throws ServletException, IOException, SQLException;

    /**
     * 用于<b>添加保存-create</b>页面的提交操作，一般与add操作方法配合使用，用于处理添加数据到数据库的操作
     * <p>
     * 一般来说，create操作方法中，如果添加成功会重新返回index视图
     * </p>
     *
     * @throws ServletException
     * @throws IOException
     * @throws SQLException
     */
    protected abstract void create() throws ServletException, IOException, SQLException;

    /**
     * 用于<b>编辑-edit</b>面的初始化显示，一般与save操作方法配合使用
     *
     * @throws ServletException
     * @throws IOException
     * @throws SQLException
     */
    protected abstract void edit() throws ServletException, IOException, SQLException;

    /**
     * 用于<b>编辑保存-save</b>页面的提交操作，一般与edit操作方法配合使用，用于处理编辑数据到数据库的操作
     * <p>
     * 一般来说，save操作方法中，如果添加成功会重新返回detail视图
     * </p>
     *
     * @throws ServletException
     * @throws IOException
     * @throws SQLException
     */
    protected abstract void save() throws ServletException, IOException, SQLException;

    /**
     * 用于<b>删除-delete</b>页面的提交操作，一般可以在detail视图中，添加一个删除按钮，直接调用此方法。
     * <p><b>执行成功后</b>，应该返回index视图</p>
     *
     * @throws ServletException
     * @throws IOException
     * @throws SQLException
     */
    protected abstract void delete() throws ServletException, IOException, SQLException;

    /**
     * 用于单条数据<b>明细-detail</b>的页面显示
     * @throws ServletException
     * @throws IOException
     * @throws SQLException
     */
    protected abstract void detail() throws ServletException, IOException, SQLException;

    private void _index(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SQLException {
        index();
    }

    private void _add(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SQLException {
        add();
    }

    private void _edit(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SQLException {
        edit();
    }

    private void _delete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SQLException {
        delete();
    }

    private void _detail(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SQLException {
        detail();
    }

    private void _create(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SQLException {
        create();
    }

    private void _save(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SQLException {
        save();
    }

    @Override
    protected final void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            processRequest(req, resp);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    protected final void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            processRequest(req, resp);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void processRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SQLException {

        this.req = req;
        this.resp = resp;
        req.setCharacterEncoding("utf-8");
        resp.setCharacterEncoding("utf-8");
        resp.setContentType("text/html;charset=UTF-8");

        String[] pathInfo = req.getServletPath().split("/");
        String action = pathInfo[pathInfo.length - 1];
        setAction(action);
        switch (action) {
            case "index":
                _index(req, resp);
                break;
            case "add":
                _add(req, resp);
                break;
            case "create":
                _create(req, resp);
                break;
            case "edit":
                _edit(req, resp);
                break;
            case "save":
                _save(req, resp);
                break;
            case "delete":
                _delete(req, resp);
                break;
            case "detail":
                _detail(req, resp);
                break;
            default:
                resp.getWriter().write("query By Default");
                break;
        }
        Boolean forward = (Boolean) req.getAttribute(getControllerName() + ".forward");
        String targetView = req.getAttribute(getControllerName() + ".targetView").toString();
        if (forward != null && forward) {
            if (targetView != null) {
                _forward(targetView);
            } else {
                _forward();
            }
        }
    }

}
