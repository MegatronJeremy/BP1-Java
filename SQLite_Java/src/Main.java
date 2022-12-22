
public class Main {

	public static void main(String[] args) {
		SQLite_java db = new SQLite_java();
		
		db.connect();

		db.printAllUsers();
		
		db.printAllUsersWithName("Milica");
		
//		db.updateUserAddress(2, "Bulvear kralaja ALeksandra");

//		db.insertUser("tasha", "bulevar");

//		db.printAllUsers();
		
		System.out.println("Pre transakcije");
		db.printAllBankAccounts();
		
		db.makeTransfer(2, 1, 1000, true);
		
		System.out.println("Posle neispravne transakcije");
		db.printAllBankAccounts();

		
		db.makeTransfer(2, 1, 1000, false);
		
		System.out.println("Posle ispravne transakcije");
		db.printAllBankAccounts();
		
		db.disconnect();

	}

}
