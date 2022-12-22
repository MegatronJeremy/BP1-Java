package lab;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Banka {
	public void connect() {
		disconnect();
		try {
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection("jdbc:sqlite:Banka_autoincrement.db");
		} catch (ClassNotFoundException | SQLException e) {
		}
	}

	public void disconnect() {
		if (conn != null) {
			try {
				conn.close();
				conn = null;
			} catch (SQLException e) {
			}
		}
	}

	public void printAllBankAccounts() {
		String sql = "select * from Racun";

		printAllFromQuery(sql);
	}

	public void printAllItems() {
		String sql = "select * from Stavka";

		printAllFromQuery(sql);
	}

	public void printAllPayments() {
		String sql = "select * from uplata";

		printAllFromQuery(sql);
	}

	private void printAllFromQuery(String sql) {

		try (Statement st = conn.createStatement()) {
			ResultSet rs = st.executeQuery(sql);
			ResultSetMetaData rsmd = rs.getMetaData();
			int columnsNumber = rsmd.getColumnCount();
			
			System.out.format("*** %s ***\n", rsmd.getTableName(1));
			
			for (int i = 1; i <= columnsNumber; i++) {
				if (i > 1)
					System.out.print("\t");
				System.out.format("%-10s", rsmd.getColumnLabel(i));
			}
			System.out.println();

			while (rs.next()) {
				for (int i = 1; i <= columnsNumber; i++) {
					if (i > 1)
						System.out.print("\t");
					System.out.format("%-10s", rs.getString(i));
				}
				System.out.println();
			}

			System.out.println();

		} catch (SQLException e) {
			System.out.println("Greska pri ispisu");
			System.out.println(e.getMessage());
		}
	}

	public int getAccountsOutOfDebt(int idFil, int idKom, boolean error) {
		try {
			conn.setAutoCommit(false);

			Queue<Integer> accountsInDebt = new LinkedList<>();
			Queue<Integer> debt = new LinkedList<>();

			getAccountsInDebt(idKom, accountsInDebt, debt);

			while (!debt.isEmpty() && !accountsInDebt.isEmpty()) {
				int ammount = debt.poll();
				int idRac = accountsInDebt.poll();
				
				updateAccount(idRac, ammount);

				updateAccountState(idRac);

				int idSta = addItem(idFil, idRac, ammount);

				addPayment(idSta);
			}
			
			if (error)
				throw new SQLException("Moja greska");
			
			conn.commit();
			
			System.out.println("Uplata je uspesna.");

		} catch (SQLException e) {
			try {
				conn.rollback();
				System.out.println(e.getMessage());
				System.out.println("Uplata je neuspesna.");
			} catch (SQLException e1) {
				throw new RuntimeException(e1);
			}
		} finally {
			try {
				conn.setAutoCommit(true);
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		return 0;
	}

	private void getAccountsInDebt(int idKom, Queue<Integer> accountsInDebt, Queue<Integer> debt) throws SQLException {
		String sql = "Select IdRac, -DozvMinus-Stanje from Racun where IdKom = ? and Stanje < -DozvMinus";

		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, idKom);

			ResultSet rs = ps.executeQuery();

			while (rs.next()) {
				accountsInDebt.add(rs.getInt(1));
				debt.add(rs.getInt(2));
			}

		} catch (SQLException e) {
			System.out.println("Greska pri dohvatanju racuna u minusu.");
			throw e;
		}
	}

	public void makePayment(int idFil, int idRac, int ammount, boolean error) {
		try {
			conn.setAutoCommit(false);

			if (ammount <= 0)
				throw new SQLException("Uplacena kolicina ne moze biti negativna.");
			updateAccount(idRac, ammount);

			updateAccountState(idRac);

			int idSta = addItem(idFil, idRac, ammount);

			addPayment(idSta);

			if (error) {
				throw new SQLException("Moja greska");
			}

			conn.commit();

			System.out.println("Uplata je uspesna.");

		} catch (SQLException e) {
			try {
				System.out.println(e.getMessage());
				System.out.println("Uplata je neuspesna.");
				conn.rollback();
			} catch (SQLException e1) {
				throw new RuntimeException(e1);
			}
		} finally {
			try {
				conn.setAutoCommit(true);
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

	}

	private void updateAccountState(int idRac) throws SQLException {
		String sql1 = "select Status, DozvMinus, Stanje from Racun where IdRac = ?";

		String sql2 = "update Racun set Status = 'A' where IdRac = ?";

		String status;
		int dozvMinus;
		int stanje;

		try (PreparedStatement ps = conn.prepareStatement(sql1)) {
			ps.setInt(1, idRac);

			ResultSet rs = ps.executeQuery();

			rs.next();
			status = rs.getString(1);
			dozvMinus = rs.getInt(2);
			stanje = rs.getInt(3);

		} catch (SQLException e) {
			System.out.println("Greska pri azuriranju stanja racuna");
			throw e;
		}

		if (!(status.equals("B") && stanje >= -dozvMinus)) {
			return;
		}

		try (PreparedStatement ps = conn.prepareStatement(sql2)) {
			ps.setInt(1, idRac);

			if (ps.executeUpdate() == 0) {
				throw new SQLException();
			}

			System.out.println("Promenjeno stanje racuna na: AKTIVAN");

		} catch (SQLException e) {
			System.out.println("Greska pri azuriranju stanja racuna");
			throw e;
		}

	}

	private void addPayment(int idSta) throws SQLException {
		String sql = "insert into Uplata(IdSta, Osnov) Values(?, 'Uplata')";

		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, idSta);
			if (ps.executeUpdate() == 0) {
				throw new SQLException();
			}
		} catch (SQLException e) {
			System.out.println("Greska pri proknjizavanju uplate:");
			throw e;
		}

	}

	private int addItem(int idFil, int idRac, int ammount) throws SQLException {
		String sql = "insert into Stavka(RedBroj, Datum, Vreme, Iznos, IdFil, IdRac) "
				+ "Values((SELECT coalesce(MAX(RedBroj), 0) from Stavka where IdRac = ?) + 1, DATE(), TIME(), ?, ?, ?)";

		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, idRac);
			ps.setInt(2, ammount);
			ps.setInt(3, idFil);
			ps.setInt(4, idRac);

			if (ps.executeUpdate() == 0) {
				throw new SQLException();
			}

			try (ResultSet rs = ps.getGeneratedKeys();) {
				if (rs.next()) {
					return rs.getInt(1);
				} else {
					throw new SQLException();
				}
			}

		} catch (SQLException e) {
			System.out.println("Greska pri proknjizavanju stavke:");
			throw e;
		}

	}

	private void updateAccount(int idRac, int ammount) throws SQLException {
		String sql = "update Racun set Stanje = Stanje + ?, BrojStavki = BrojStavki + 1 " + "where IdRac = ?";

		try (PreparedStatement ps = conn.prepareStatement(sql);) {
			ps.setInt(1, ammount);
			ps.setInt(2, idRac);

			if (ps.executeUpdate() == 0) {
				throw new SQLException();
			}
		} catch (SQLException e) {
			System.out.println("Greska pri promeni stanja racuna:");
			throw e;
		}
	}

	private Connection conn = null;
}
