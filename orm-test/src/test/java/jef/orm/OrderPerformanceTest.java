package jef.orm;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import jef.database.DbClient;
import jef.database.DbClientBuilder;
import jef.database.DebugUtil;
import jef.database.ORMConfig;
import jef.database.OperateTarget;
import jef.database.Transaction;
import jef.database.jdbc.result.IResultSet;
import jef.database.jdbc.result.ResultSetContainer;
import jef.database.jdbc.result.ResultSetHolder;
import jef.database.jdbc.rowset.CachedRowSetImpl;
import jef.database.wrapper.clause.InMemoryOrderBy;
import jef.orm.onetable.model.Foo;
import jef.tools.string.RandomData;

import org.junit.BeforeClass;
import org.junit.Test;

public class OrderPerformanceTest {

	private static ResultSet rs1;
	private static ResultSet rs2;
	private static ResultSet rs3;

	@Test
	public void addRecord() throws SQLException {
		ORMConfig.getInstance().setCacheDebug(true);
		DbClient db = new DbClientBuilder().setEnhancePackages("jef.orm.onetable.model").build();
		Transaction tx = db.startTransaction();
		db.createTable(Foo.class);
		List<Foo> list = new ArrayList<Foo>();
		for (int i = 0; i < 10000; i++) {
			Foo foo = new Foo();
			foo.setName(RandomData.randomChineseName());
			list.add(foo);
		}
		tx.batchInsert(list);
		tx.commit(true);
		db.close();
	}

	@BeforeClass
	public static void prepare() throws SQLException {
		DbClient db = new DbClientBuilder().build();

		CachedRowSetImpl c1 = new CachedRowSetImpl();
		CachedRowSetImpl c2 = new CachedRowSetImpl();
		CachedRowSetImpl c3 = new CachedRowSetImpl();

		Connection conn = DebugUtil.getConnection(db.getSqlTemplate(null));
		Statement st = conn.createStatement();
		ResultSet rs;
		rs = st.executeQuery("select * from foo where id>0 and id<= 25000");
		c1.populate(rs);
		rs.close();
		rs = st.executeQuery("select * from foo where id>25000 and id<= 30000");
		c2.populate(rs);
		rs.close();

		rs = st.executeQuery("select * from foo where id>30000 and id<=35000");
		c3.populate(rs);
		rs.close();

		System.out.println(c1.size());
		System.out.println(c2.size());
		System.out.println(c3.size());

		rs1 = c1;
		rs2 = c2;
		rs3 = c3;
		st.close();
		db.close();
	}

	@Test
	public void run1() throws SQLException {
		DbClient db = new DbClientBuilder().build();
		testOrder1Count(db, 1); // 预热
		testOrder1(db, 100);// 正式测试
		db.close();
	}

	public void testOrder1(DbClient db, int count) throws SQLException {
		OperateTarget tx = (OperateTarget) db.getSqlTemplate(null);
		ResultSetContainer mrs = new ResultSetContainer(false);
		mrs.add(new ResultSetHolder(tx, null, rs1));
		mrs.add(new ResultSetHolder(tx, null, rs2));
		mrs.add(new ResultSetHolder(tx, null, rs3));
		mrs.setInMemoryOrder(new InMemoryOrderBy(new int[] { 1 }, new boolean[] { true }));
		testRsPerformces(mrs, "simple", count);// 开始测试
	}

	private void testOrder1Count(DbClient db, int count) throws SQLException {
		OperateTarget tx = (OperateTarget) db.getSqlTemplate(null);
		ResultSetContainer mrs = new ResultSetContainer(false);
		mrs.add(new ResultSetHolder(tx, null, rs1));
		mrs.add(new ResultSetHolder(tx, null, rs2));
		mrs.add(new ResultSetHolder(tx, null, rs3));
		mrs.setInMemoryOrder(new InMemoryOrderBy(new int[] { 1 }, new boolean[] { true }));
		testRsPerformcesCount(mrs, "simple", count);// 开始测试
	}

	private void testRsPerformces(ResultSetContainer mrs, String name, int count) throws SQLException {
		long start = System.currentTimeMillis();
		for (int x = 0; x < count; x++) {
			// int n=0;
			IResultSet rs = mrs.toProperResultSet(null);
			while (rs.next()) {
				// n++;
			}
			// System.out.println(n);
			rs1.beforeFirst();
			rs2.beforeFirst();
			rs3.beforeFirst();
		}
		long cost = System.currentTimeMillis() - start;
		if (count > 5) {
			System.out.println("----- " + name + "运行" + count + "次，耗时" + cost);
		}
	}

	private void testRsPerformcesCount(ResultSetContainer mrs, String name, int count) throws SQLException {
		for (int x = 0; x < count; x++) {
			int n = 0;
			IResultSet rs = mrs.toProperResultSet(null);
			while (rs.next()) {
				n++;
			}
			System.out.println("总数" + n);
			rs1.beforeFirst();
			rs2.beforeFirst();
			rs3.beforeFirst();
		}
	}


}
