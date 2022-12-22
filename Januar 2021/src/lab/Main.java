package lab;

public class Main {

	public static void main(String[] args) {
		Banka banka = new Banka();

		try {
			banka.connect();

			System.out.println("Before payment:");

			banka.printAllBankAccounts();
			banka.printAllItems();
			banka.printAllPayments();

//			banka.makePayment(1, 6, 10000, false);
			
			banka.getAccountsOutOfDebt(4, 3, false);

			System.out.println();
			System.out.println("After payment:");

			banka.printAllBankAccounts();
			banka.printAllItems();
			banka.printAllPayments();
		} finally {
			banka.disconnect();
		}

	}
}
