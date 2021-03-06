package programs.bsts;

import programs.nodes.DataStructureFactory;
import programs.nodes.NodeBST;
import templates.heap.BSTTemplate;

public class BstMax {

    private NodeBST findMax(NodeBST root) {
        if (root == null)
            return null;
        else if (root.right == null)
            return root;
        else
            return findMax(root.right);
    }

    private boolean check(NodeBST root) {
        if (root == null)
            return true;
        else
            return check(root.left) && check(root.right);
    }

    public void main1(NodeBST x) {
		// pre : x::bst()
        check(x);
		// inv : x::bst()
        findMax(x);
		// post : x::bst()
        assert (new BSTTemplate()).check(x).res == 1;
    }
}
