// IGNORE_K2
enum Color implements Runnable {
  WHITE, BLACK, RED, YELLOW, BLUE;

  public void run() {
   System.out.println("name()=" + name() +
       ", toString()=" + toString());
  }
}