import json, base64, uuid
T='tex/'
def b64(p): return 'data:image/png;base64,'+base64.b64encode(open(p,'rb').read()).decode()
def U(): return str(uuid.uuid4())
def faces(u,v,w,h,d,tex):
    return {"north":{"uv":[u+d,v+d,u+d+w,v+d+h],"texture":tex},
            "east":{"uv":[u,v+d,u+d,v+d+h],"texture":tex},
            "south":{"uv":[u+2*d+w,v+d,u+2*d+2*w,v+d+h],"texture":tex},
            "west":{"uv":[u+d+w,v+d,u+2*d+w,v+d+h],"texture":tex},
            "up":{"uv":[u+d+w,v+d,u+d,v],"texture":tex},
            "down":{"uv":[u+d+2*w,v,u+d+w,v+d],"texture":tex}}
def cube(name,frm,to,uv,tex=0,inflate=0):
    w,h,d=[to[i]-frm[i] for i in range(3)]
    return {"name":name,"box_uv":True,"rescale":False,"locked":False,"render_order":"default",
            "allow_mirror_modeling":True,"from":frm,"to":to,"autouv":0,"color":0,
            "origin":[0,0,0],"uv_offset":uv,"inflate":inflate,
            "faces":faces(uv[0],uv[1],w,h,d,tex),"type":"cube","uuid":U()}
def group(name,origin,children,rotation=None):
    return {"name":name,"origin":origin,"rotation":rotation or [0,0,0],"color":0,"uuid":U(),
            "export":True,"mirror_uv":False,"isOpen":True,"locked":False,"visibility":True,
            "autouv":0,"children":children}
def tex(name,path,i):
    return {"path":"","name":name,"folder":"entity","namespace":"xenowarriors","id":str(i),
            "width":64,"height":64,"uv_width":64,"uv_height":64,"particle":False,
            "render_mode":"default","render_sides":"auto","visible":True,"internal":True,"saved":False,
            "uuid":U(),"source":b64(path)}
def build(name, wings):
    E=[];O=[]
    def part(gname,origin,base,over):
        c1=cube(gname,*base); c2=cube(gname+" Layer",*over)
        E.extend([c1,c2]); O.append(group(gname,origin,[c1["uuid"],c2["uuid"]]))
    part("Head",[0,24,0],([-4,24,-4],[4,32,4],[0,0]),([-4,24,-4],[4,32,4],[32,0],0,0.5))
    part("Body",[0,24,0],([-4,12,-2],[4,24,2],[16,16]),([-4,12,-2],[4,24,2],[16,32],0,0.25))
    part("RightArm",[5,22,0],([4,12,-2],[8,24,2],[40,16]),([4,12,-2],[8,24,2],[40,32],0,0.25))
    part("LeftArm",[-5,22,0],([-8,12,-2],[-4,24,2],[32,48]),([-8,12,-2],[-4,24,2],[48,48],0,0.25))
    part("RightLeg",[1.9,12,0],([0,0,-2],[4,12,2],[0,16]),([0,0,-2],[4,12,2],[0,32],0,0.25))
    part("LeftLeg",[-1.9,12,0],([-4,0,-2],[0,12,2],[16,48]),([-4,0,-2],[0,12,2],[0,48],0,0.25))
    textures=[tex(name+".png",T+name+".png",0)]
    if wings:
        textures.append(tex("rhalix_wings.png",T+"rhalix_wings.png",1))
        L=[9,11,13,15,17,19,21]; span=21
        for side,sgn in (("LeftWing",-1),("RightWing",1)):
            px=1.5*sgn; top=21.5; z0=2.0; kids=[]
            x0,x1=sorted([px, px+sgn*span])
            b=cube(side+" Bone",[x0,top,z0],[x1,top+2,z0+1],[0,0],1); E.append(b); kids.append(b["uuid"])
            for i,l in enumerate(L):
                a,c=sorted([px+sgn*i*3, px+sgn*(i+1)*3])
                f=cube(f"{side} Feather {i+1}",[a,top-l,z0],[c,top,z0+1],[i*8,4],1)
                E.append(f); kids.append(f["uuid"])
            O.append(group(side,[px,top+1,z0+0.5],kids,[0,-20*sgn,25*sgn]))
    model={"meta":{"format_version":"4.10","model_format":("free" if wings else "modded_entity"),"box_uv":True},
           "name":name,"model_identifier":name,"modded_entity_version":"Mojmaps",
           "modded_entity_flip_y":True,"visible_box":[1,2,0],
           "resolution":{"width":64,"height":64},"elements":E,"outliner":O,"textures":textures}
    json.dump(model,open(f'/mnt/user-data/outputs/blockbench/{name}.bbmodel','w'),indent=1)
build("mikiko",False); build("rhalix",True)
print("ok")
