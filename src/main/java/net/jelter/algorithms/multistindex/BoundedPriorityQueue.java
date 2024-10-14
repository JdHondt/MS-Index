package net.jelter.algorithms.multistindex;

import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.List;

public final class BoundedPriorityQueue<T> {

    private final PriorityQueue<T> queue; /* backing data structure */
    private final Comparator<? super T> comparator;
    private final int maxSize;

    /**
     * Constructs a {@link BoundedPriorityQueue} with the specified
     * {@code maxSize} and {@code comparator}.
     *
     * @param maxSize    - The maximum size the queue can reach, must be a positive
     *                   integer.
     * @param comparator - The comparator to be used to compare the elements in the
     *                   queue, must be non-null.
     */
    public BoundedPriorityQueue(final int maxSize, final Comparator<? super T> comparator) {
        this.queue = new PriorityQueue<T>(reverse(comparator));
        this.comparator = comparator;
        this.maxSize = maxSize;
    }

    private static <T> Comparator<T> reverse(final Comparator<T> comparator) {
        return (o1, o2) -> comparator.compare(o2, o1);
    }

    public static <T> BoundedPriorityQueue<T> create(final int maxSize,
                                                     final Comparator<? super T> comparator) {
        return new BoundedPriorityQueue<>(maxSize, comparator);
    }

    /**
     * Adds an element to the queue. If the queue contains {@code maxSize}
     * elements, {@code e} will be compared to the lowest element in the queue
     * using {@code comparator}. If {@code e} is greater than or equal to the
     * lowest element, that element will be removed and {@code e} will be added
     * instead. Otherwise, the queue will not be modified and {@code e} will not
     * be added.
     *
     * @param t - Element to be added, must be non-null.
     */
    public void add(final T t) {
        if (t == null) {
            throw new NullPointerException("cannot add null to the queue");
        }
        while (queue.size() >= maxSize) {
            final T maxElement = queue.peek();
            int compInt = comparator.compare(maxElement, t);
            if (compInt < 0) { // max element < t
                return;
            } else if (compInt > 0) { // t < max element
//                Poll the max element and ask question again
                queue.poll();
            } else { // t == max element
                queue.add(t);
                return;
            }
        }
        queue.add(t);
    }

    public void addAll(final List<T> list) {
        for (T t : list) {
            add(t);
        }
    }

    public T peek() {
        return queue.peek();
    }

    public int size() {
        return queue.size();
    }

    public List<T> asOrderedList() {
        List<T> list = new ArrayList<>(queue);
        list.sort(comparator);
        return list;
    }

}