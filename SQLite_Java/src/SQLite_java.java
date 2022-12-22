import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/*
 * Mozemo raditi u eclipse ili IntelliJ
 */

public class SQLite_java {
	private Connection conn;

	public void connect() {
		disconnect();
		try {
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection("jdbc:sqlite:Banka_autoincrement.db");
			/*
			 * nisu neke jako specijalne stvari koje se ocekuje da zname cilj je da naucimo
			 * sustinu: postupci, transakcije, ne sam jezik za lab imamo konekciju, upite,
			 * zatvaranje konekcije (na pokaznom delu laba) u nadoknadama gotov kod
			 */
		} catch (ClassNotFoundException | SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public void disconnect() {
		if (conn != null) {
			try {
				conn.close();
				conn = null;
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

	}

	public void printAllUsers() {
		/*
		 * Bitno da se stvari stvarno izvrsavaju (bez nekih gubitaka)
		 */
		String sql = "select * from Komitent";
		/*
		 * Unutar viticastih zagrada - ako baci izuzetak ne moramo da radimo close
		 */
		try (Statement stmt = conn.createStatement()) {
			ResultSet rs = stmt.executeQuery(sql);
			System.out.println("All users");

			// next tek predje u prvi red ukoliko postoji i vrati true
			while (rs.next()) {
				/*
				 * Stvari u bazi numerisane od 1
				 */
				System.out.println(rs.getInt(1) // prosledjujem column index
						+ "\t" + rs.getString(2) + "\t" + rs.getString("Adresa")); // moze se i dohvatati na osnovu
																					// naziva
			}
			rs.close();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public void printAllUsersWithName(String name) {
		/*
		 * Sve korisnike po imenu? Sta ako neko namerno unese glupost?
		 */
//		String sql = "select * from Komitent where name='" + name + "'";
		String sql = "select * from Komitent where Naziv = ?";
		/*
		 * prosledim kao name:
		 * "tasha'; DROP TABLE User; SELECT * from USER where name = 'a" select * from
		 * User where username = 'tasha'; delete from User; //' Desi se SQL injection
		 * Nikada direktno da sastavljamo preko stringa!!! Postoji nacin da direktno
		 * generisemo upit sa dodavanjem promenljivih Prepare statement - koristimo
		 * upitnike
		 */
		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			// setujemo vrednost - preparedStatement
			stmt.setString(1, name);
			// sada zna sve sto treba da uradi - PreparedStatement svuda - kod bezbedan
			try (ResultSet rs = stmt.executeQuery()) {
				System.out.println("All users with name " + name);

				while (rs.next()) {
					System.out.println(rs.getInt(1) // prosledjujem column index
							+ "\t" + rs.getString(2) + "\t" + rs.getString("Adresa")); // moze se i dohvatati na osnovu
																						// naziva
				}
			} catch (SQLException e) {
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public void printAllBankAccounts() {
		String sql = "select * from Racun";
		try (Statement stmt = conn.createStatement()) {
			ResultSet rs = stmt.executeQuery(sql);
			System.out.println("All account");

			while (rs.next()) {
				System.out.println(rs.getInt(1) + "\t" + rs.getInt(5) + "\t" + rs.getInt(7));
			}
			rs.close();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

	}

	public boolean updateUserAddress(int idKom, String address) {
		String sql = "update Komitent set adresa = ? where idKom = ?";
		// jednom nesto pripremljeno moze vise puta da se izvrsava
		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setInt(2, idKom);
			stmt.setString(1, address);

			// nije bas dobro - posto moze da ne vrati nista ovaj query
			return stmt.executeUpdate() > 0; // vraca broj izmenjenih redova
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public int insertUser(String name, String address) {
		// primarni kljuc nije obavezan - autoinkrement
		String sql = "insert into Komitent (Naziv, Adresa) values (?, ?)";
		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, name);
			stmt.setString(2, address);

			// kako da saznam sta je id?
			if (stmt.executeUpdate() > 0) {
				// vraca resultset - jer mozemo vise stvari promeniti / dodati
				try (ResultSet rs = stmt.getGeneratedKeys()) {
					if (rs.next()) {
						int id = rs.getInt(1); // uvek ce imati samo jednu kolonu
						System.out.println("Dodat je user sa id = " + id);
						return id;
					}
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
			}

			return -1;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public void makeTransfer(int idRacFrom, int idRacTo, int iznos, boolean error) {
		/*
		 * najbitnija za nas - transakcija - deo koda koji zakljucavamo - celokupno ili
		 * nece biti uopste izvrsena Desi se softverska greska? Treba da vratim na staro
		 * stanje - Revert U redu? Commit transaction Obezbedjujemo atomicnost
		 * transakcije Podrazumevani princip update-a (execute query) - autocommit
		 */
		String sql = "update Racun set Stanje = Stanje + ? where IdRac = ?";

		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			conn.setAutoCommit(false);

			ps.setInt(2, idRacFrom);
			ps.setInt(1, -iznos);
			ps.executeUpdate(); // ovde bi i trebali neku proveru raditi

			if (error) {
				throw new Exception("Moja greska");
			}

			ps.setInt(2, idRacTo);
			ps.setInt(1, iznos);
			ps.executeUpdate(); // ovde bi i trebali neku proveru raditi

			conn.commit(); // sacuvam promene
		} catch (Exception e) {
			try {

				/*
				 * To avoid conflicts during a transaction, a DBMS uses locks, mechanisms for
				 * blocking access by others to the data that is being accessed by the
				 * transaction. (Note that in auto-commit mode, where each statement is a
				 * transaction, locks are held for only one statement.) After a lock is set, it
				 * remains in force until the transaction is committed or rolled back. For
				 * example, a DBMS could lock a row of a table until updates to it have been
				 * committed. The effect of this lock would be to prevent a user from getting a
				 * dirty read, that is, reading a value before it is made permanent. (Accessing
				 * an updated value that has not been committed is considered a dirty read
				 * because it is possible for that value to be rolled back to its previous
				 * value. If you read a value that is later rolled back, you will have read an
				 * invalid value.)
				 * 
				 */
				conn.rollback();
			} catch (SQLException e1) {
				throw new RuntimeException(e1);
			}
		} finally {
			// jer mi je podrazumevano ukljucen
			try {
				conn.setAutoCommit(true);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

	}

}
