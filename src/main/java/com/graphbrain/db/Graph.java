package com.graphbrain.db;

import java.util.List;

public abstract class Graph {

	private Backend back;
	
	public Graph() {
		back = new LevelDbBackend();
	}
	
	public Vertex get(String id) {
		return back.get(id, VertexType.type(id));
	}
	
	public boolean exists(String id) {
		return get(id) != null;
	}
	
	public UserNode getUserNode(String id) {
		return (UserNode)back.get(id, VertexType.User);
	}
	
	private String idFromEmail(String email) {
		String userName = back.usernameByEmail(email);
		if (userName == null) {
			return null;
		}
		else {
			return ID.idFromUsername(userName);
		}
	}
	
	public boolean usernameExists(String username) {
		return exists(ID.idFromUsername(username));
	}
	
	public boolean emailExists(String email) {
		String userName = back.usernameByEmail(email);
	    return userName != null; 
	}
	
	public UserNode findUser(String login) {
		if (exists(ID.idFromUsername(login))) {
		  	  return getUserNode(ID.idFromUsername(login));
		}
		else {
			String uid = idFromEmail(login);
		    if (uid == null) {
		    	return null;
		    }
		    else if (exists(uid)) {
		  	    return getUserNode(idFromEmail(login));
		    }
		    else {
		        return null;
		    }
		}
	}
	
	public UserNode getUserNodeByUsername(String username) {
		if (exists(ID.idFromUsername(username))) {
			return getUserNode(ID.idFromUsername(username));
		}
		else {
			return null;
		}
	}
	
	public UserNode createUser(String username, String name, String email, String password, String role) {
		UserNode userNode = UserNode.create(username, name, email, password, role);
		back.put(userNode);
		if (!email.equals("")) {
		    back.associateEmailToUsername(email, username);
		}

		return userNode;
	}
	
	public UserNode attemptLogin(String login, String password) {
		UserNode userNode = findUser(login);

		// user does not exist
		if (userNode == null) {
			return null;
		}
		    
		// password is incorrect
		if (!userNode.checkPassword(password)) {
			return null;
		}

		// ok, create new session
		userNode.newSession();
		back.update(userNode);
		return userNode;
	}
	
	public UserNode forceLogin(String login) {
		UserNode userNode = findUser(login);

		// user does not exist
		if (userNode == null) {
			return null;
		}

		// ok, create new session
		userNode.newSession();
		back.update(userNode);
		return userNode;
	}
	
	public List<Vertex> allUsers() {
		return back.listByType(VertexType.User);
	}

/*

  def onPut(vertex: Vertex) = {}


  def update(vertex: Vertex): Vertex = put(vertex)


  def remove(vertex: Vertex): Vertex = {
    ldebug("remove" + vertex.id)

    val id = vertex.id
    
    // remove associated degree
    delDegree(id)

    // remove associated edges
    val nEdges = neighborEdges(id)
    for (e <- nEdges) delrel(e)

    vertex match {
      case t: TextNode => {
        val template = if (ID.isInUserSpace(id)) backend.tpUserSpace else backend.tpGlobal
        template.deleteRow(id)
      }
      case et: EdgeType => {
        delInstances(id)
        backend.tpEdgeType.deleteRow(id)
      }
      case r: RuleNode => backend.tpGlobal.deleteRow(id)
      case u: URLNode => {
        val template = if (ID.isInUserSpace(id)) backend.tpUserSpace else backend.tpGlobal
        template.deleteRow(id)
      }
      case s: SourceNode => backend.tpGlobal.deleteRow(id)
      case u: UserNode => backend.tpUser.deleteRow(id)
      case u: ContextNode => backend.tpUserSpace.deleteRow(id)
    }

    vertex
  }


  def addrel(edge: Edge): Edge = {
    ldebug("addrel " + edge)

    if (!relExists(edge)) {
      incInstances(edge.edgeType)
      for (i <- 0 until edge.participantIds.size) {
        val p = edge.participantIds(i)
        addEdgeEntry(p, edge)
        incVertexEdgeType(p, edge.edgeType, i)
        incDegree(p)
      }
    }

    edge
  }


  def addrel(edgeType: String, participants: List[String]): Edge = addrel(Edge(edgeType, participants))


  def delrel(edge: Edge): Unit = {
    ldebug("delrel " + edge)

    if (relExists(edge)) {
      decInstances(edge.edgeType)
      for (i <- 0 until edge.participantIds.size) {
        val p = edge.participantIds(i)
        delEdgeEntry(p, edge)
        decVertexEdgeType(p, edge.edgeType, i)
        decDegree(p)
      }
    }
  }


  def delrel(edgeType: String, participants: List[String]): Unit = delrel(Edge(edgeType, participants))


  def neighborEdges(nodeId: String, edgeType: String = "", relPos: Integer = -1): Set[Edge] = {
    //ldebug("neighborEdges " + nodeId + "; edgeType: " + edgeType + "; pos: " + relPos)

    try {
      val eset = MSet[Edge]()

      val query = HFactory.createSliceQuery(backend.ksp, StringSerializer.get(),
                                                      new CompositeSerializer(), StringSerializer.get())
      
      query.setKey(nodeId)
      query.setColumnFamily("edges")

      val minPos: java.lang.Integer = Integer.MIN_VALUE
      val maxPos: java.lang.Integer = Integer.MAX_VALUE

      val minEdgeType = if (edgeType == "") String.valueOf(Character.MIN_VALUE) else edgeType
      val maxEdgeType = if (edgeType == "") String.valueOf(Character.MAX_VALUE) else edgeType

      val start = new Composite()
      start.addComponent(minEdgeType, StringSerializer.get())
      start.addComponent(minPos, IntegerSerializer.get())
      start.addComponent(String.valueOf(Character.MIN_VALUE), StringSerializer.get())
      start.addComponent(String.valueOf(Character.MIN_VALUE), StringSerializer.get())
      start.addComponent(String.valueOf(Character.MIN_VALUE), StringSerializer.get())
        
      val finish = new Composite()
      finish.addComponent(maxEdgeType, StringSerializer.get())
      finish.addComponent(maxPos, IntegerSerializer.get())
      finish.addComponent(String.valueOf(Character.MAX_VALUE), StringSerializer.get())
      finish.addComponent(String.valueOf(Character.MAX_VALUE), StringSerializer.get())
      finish.addComponent(String.valueOf(Character.MAX_VALUE), StringSerializer.get())

      val it = new ColumnSliceIterator[String, Composite, String](query, start, finish, false)
      while (it.hasNext()) {
        val column = it.next()
        val pos: Integer = column.getName().get(1, IntegerSerializer.get())

        if ((relPos < 0) || (relPos != pos)) {
          val edgeType = column.getName().get(0, StringSerializer.get())
          val node1 = column.getName().get(2, StringSerializer.get())
          val node2 = column.getName().get(3, StringSerializer.get())
          val nodeN = column.getName().get(4, StringSerializer.get())
          val edge = Edge.fromEdgeEntry(nodeId, edgeType, pos, node1, node2, nodeN)
          eset += edge
        }
      }

      eset.toSet
    }
    catch {
      case e => Set[Edge]()
    }
  }


  def nodesFromEdgeSet(edgeSet: Set[Edge]): Set[String] = {
    val nset = MSet[String]()

    for (e <- edgeSet) {
      for (pid <- e.participantIds)
        nset += pid
    }

    nset.toSet
  }


  def neighbors(nodeId: String): Set[String] = {
    ldebug("neighbors " + nodeId)

    val nedges = neighborEdges(nodeId)
    nodesFromEdgeSet(nedges) + nodeId
  }


  private def edgeEntryKey(nodeId: String, edge: Edge) = {
    ldebug("edgeEntryKey nodeId: " + nodeId + "; edge: " + edge)

    val pos: java.lang.Integer = edge.participantIds.indexOf(nodeId)    
    // TODO: throw exception if pos == -1

    val participants = edge.participantIds.filterNot(x => x == nodeId)
    val numberOfParticipants = participants.length

    val node2 = if (numberOfParticipants > 1) participants(1) else ""

    val extraNodes =
      if (numberOfParticipants > 2)
        participants.slice(2, numberOfParticipants).reduceLeft(_ + " " + _)
      else
        ""

    val c = new Composite()
    c.addComponent(edge.edgeType, StringSerializer.get())
    c.addComponent(pos, IntegerSerializer.get())
    c.addComponent(participants(0), StringSerializer.get())
    c.addComponent(node2, StringSerializer.get())
    c.addComponent(extraNodes, StringSerializer.get())

    c
  }


  private def addEdgeEntry(nodeId: String, edge: Edge) = {
    ldebug("addEdgeEntry nodeId: " + nodeId + "; edge: " + edge)

    val colKey = edgeEntryKey(nodeId, edge)

    val col = HFactory.createColumn(colKey, "", new CompositeSerializer(), StringSerializer.get())
    val mutator = HFactory.createMutator(backend.ksp, StringSerializer.get())
    mutator.addInsertion(nodeId, "edges", col)
    mutator.execute()
  }


  private def delEdgeEntry(nodeId: String, edge: Edge) = {
    ldebug("delEdgeEntry nodeId: " + nodeId + "; edge: " + edge)

    val colKey = edgeEntryKey(nodeId, edge)

    val mutator = HFactory.createMutator(backend.ksp, StringSerializer.get())
    mutator.addDeletion(nodeId, "edges", colKey, new CompositeSerializer())
    mutator.execute()
  }


  private def incVertexEdgeType(nodeId: String, edgeType: String, position: Int) = {
    ldebug("incVertexEdgeType nodeId: " + nodeId + "; edgeType: " + edgeType + "; position: " + position)

    val relId = ID.relationshipId(edgeType, position)

    val col = HFactory.createCounterColumn(relId, 1L, StringSerializer.get())
    val mutator = HFactory.createMutator(backend.ksp, StringSerializer.get())
    mutator.insertCounter(nodeId, "vertexedgetype", col)
    mutator.execute()
  }


  private def decVertexEdgeType(nodeId: String, edgeType: String, position: Int): Boolean = {
    ldebug("decVertexEdgeType nodeId: " + nodeId + "; edgeType: " + edgeType + "; position: " + position)

    val relId = ID.relationshipId(edgeType, position)

    // get current count
    val query = HFactory.createCounterColumnQuery(backend.ksp, StringSerializer.get(), StringSerializer.get())
    query.setColumnFamily("vertexedgetype").setKey(nodeId).setName(relId)
    val counter = query.execute().get()
    if (counter == null) return false
    val count = counter.getValue()

    // decrement or delete
    val mutator = HFactory.createMutator(backend.ksp, StringSerializer.get())
    if (count <= 1) {
      mutator.addDeletion(nodeId, "vertexedgetype", relId, StringSerializer.get())
      mutator.execute()
    }
    else {
      val col = HFactory.createCounterColumn(relId, -1L, StringSerializer.get())
      mutator.insertCounter(nodeId, "vertexedgetype", col)
      mutator.execute()
    }

    true
  }

  private def incDegree(vertexId: String) = {
    ldebug("incDegree " + vertexId)

    val col = HFactory.createCounterColumn("degree", 1L, StringSerializer.get())
    val mutator = HFactory.createMutator(backend.ksp, StringSerializer.get())
    mutator.insertCounter(vertexId, "degrees", col)
    mutator.execute()
  }


  private def decDegree(vertexId: String) = {
    ldebug("decDegree " + vertexId)

    val col = HFactory.createCounterColumn("degree", -1L, StringSerializer.get())
    val mutator = HFactory.createMutator(backend.ksp, StringSerializer.get())
    mutator.insertCounter(vertexId, "degrees", col)
    mutator.execute()
  }


  private def delDegree(vertexId: String) = {
    ldebug("delDegree " + vertexId)

    val mutator = HFactory.createMutator(backend.ksp, StringSerializer.get())
    mutator.addDeletion(vertexId, "degrees", "degree", StringSerializer.get())
    mutator.execute()
  }

  private def incInstances(edgeType: String) = {
    ldebug("incInstances " + edgeType)

    val col = HFactory.createCounterColumn("instances", 1L, StringSerializer.get())
    val mutator = HFactory.createMutator(backend.ksp, StringSerializer.get())
    mutator.insertCounter(edgeType, "instances", col)
    mutator.execute()
  }


  private def decInstances(edgeType: String) = {
    ldebug("decInstances " + edgeType)

    val col = HFactory.createCounterColumn("instances", -1L, StringSerializer.get())
    val mutator = HFactory.createMutator(backend.ksp, StringSerializer.get())
    mutator.insertCounter(edgeType, "instances", col)
    mutator.execute()
  }


  private def delInstances(edgeType: String) = {
    ldebug("delInstances " + edgeType)

    val mutator = HFactory.createMutator(backend.ksp, StringSerializer.get())
    mutator.addDeletion(edgeType, "instances", "instances", StringSerializer.get())
    mutator.execute()
  }


  def relExistsOnVertex(id: String, edge: Edge): Boolean = {
    ldebug("relExistsOnVertex id: " + id + "; edge: " + edge)

    val ckey = edgeEntryKey(id, edge)
    val res = backend.tpEdges.queryColumns(id, Arrays.asList(ckey))
    res.hasResults()
  }

  
  def relExists(edge: Edge): Boolean = {
    ldebug("relExists: " + edge)

    edge.participantIds.exists(relExistsOnVertex(_, edge))
  }


  def getOrNull(id: String): Vertex = {
    ldebug("getOrNull: " + id)

    try {
      get(id)
    }
    catch {
      case e: KeyNotFound => {
        ldebug("KeyNotFound " + id)
        null
      }
    }
  }

  def getSourceNode(id: String): SourceNode = {
  	ldebug("getSourceNode: " + id)
    get(id) match {
  		case x: SourceNode => x
  		case v: Vertex => throw WrongVertexType("on vertex: " + id + " (expected SourceNode)")
  	}
  }

  def createAndConnectVertices(edgeType: String, participants: Array[Vertex]) = {
    ldebug("createAndConnectVertices edgeType: " + edgeType + "; participants: " + participants)
    for (v <- participants) {
      if (!exists(v.id)) {
        put(v)
      }
    }

    val ids = for (v <- participants) yield v.id
    addrel(edgeType.replace(" ", "_"), ids.toList)
  }

  def createTextNode(namespace: String="", text: String="", summary: String="") = TextNode(this, namespace, text, summary)

  def createEdgeType(id: String="", label: String="") = EdgeType(this, id, label)

  def createSourceNode(id: String="") = SourceNode(this, id)

  def createURLNode(url: String="", userId: String = "", title: String="") = URLNode(this, url, userId, title)

  def createUserNode(id: String="", username: String="", name: String="",
  email: String="", pwdhash: String="", role: String="", session: String="",
  sessionTs: Long= -1, lastSeen: Long= -1, contexts: List[ContextNode]=null, summary: String="") = UserNode(this, id, username, name, email, pwdhash, role, session, sessionTs, lastSeen, contexts, summary)

  def createRuleNode(store: VertexStore, id: String="", rule: String="") = RuleNode(this, id, rule)
  */
}


/*
object VertexStore {
  def apply(clusterName: String, keyspaceName: String) = new VertexStore(clusterName, keyspaceName)

  def main(args : Array[String]) : Unit = {
    val store = new VertexStore()

    val edges = store.neighborEdges("user/telmo_menezes")
    for (e <- edges) println(e)

    var snode = store.getUserNode("user/telmo_menezes").updateSummary
    println(snode.summary)
  }
}*/