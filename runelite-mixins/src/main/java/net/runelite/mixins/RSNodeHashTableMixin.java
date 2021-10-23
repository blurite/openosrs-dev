package net.runelite.mixins;

import net.runelite.api.Node;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.runelite.api.events.HashTableNodePut;
import net.runelite.api.mixins.*;
import net.runelite.rs.api.RSClient;
import net.runelite.rs.api.RSNodeHashTable;

@Mixin(RSNodeHashTable.class)
public abstract class RSNodeHashTableMixin implements RSNodeHashTable
{
	@Shadow("client")
	private static RSClient client;

	@Inject
	@Override
	public Collection<Node> getNodes()
	{
		// Copied in RSWidgetMixin.getParentId to reduce allocations
		List<Node> nodes = new ArrayList<Node>();

		Node[] buckets = getBuckets();
		for (int i = 0; i < buckets.length; ++i)
		{
			Node node = buckets[i];

			// It looks like the first node in the bucket is always
			// a sentinel
			Node cur = node.getNext();
			while (cur != node)
			{
				nodes.add(cur);
				cur = cur.getNext();
			}
		}

		return nodes;
	}

	@Copy("put")
	@Replace("put")
	@SuppressWarnings({"InfiniteRecursion", "unchecked"})
	public void copy$put(Node node, long value) {
		copy$put(node, value);
		client.getCallbacks().post(new HashTableNodePut(this, node, value));
	}
}
