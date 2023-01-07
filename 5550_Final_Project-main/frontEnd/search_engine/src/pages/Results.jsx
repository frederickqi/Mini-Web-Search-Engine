import React,{useEffect,useState} from 'react';
import { AudioOutlined } from '@ant-design/icons';
import {Layout, Menu, Image, Row, Col,Input, Divider,Pagination, List} from 'antd';
// import { HomeOutlined, SearchOutlined, PlusCircleOutlined, BellOutlined } from '@ant-design/icons';
import logo from "../images/LogoResult.png"
import { useNavigate } from 'react-router-dom'
import "./Results.css"
import fakeData from '../fakeData';
import { getPageResults } from '../router/fetcher'
import staticMethods from 'antd/es/message';

const { Header, Content, Footer } = Layout;

export default function Results(){
    // let SearchData = {};
    
    const [SearchData, setSearchData] = useState({})
    const[isLoading, setLoading]=useState(true);
    const[keyWord, setKeyWord] = useState("")

    const newInput = ""
    const navigate = useNavigate()
    const backToHome = () => {
        navigate('/')
    }
    const onSearch = (value) => {
      console.log(value);
      setKeyWord(value);
      localStorage.setItem("mainInput",value)

    }
    const { Search } = Input;
    const suffix = (
    <AudioOutlined
        style={{
        fontSize: 16,
         color: 'ffffff',
        }}
        />
    );

    const getSearchData = async()=>{
      const data = await getPageResults(localStorage.getItem("mainInput"));
      // const data = await fakeData;
      //console.log(data);
//       const dataSorted = [...data].sort((a,b)=>b.rank_score-a.rank_score).slice(0,100);
      setSearchData(data);
      setLoading(false);
      console.log(SearchData);
    }
    
    useEffect(() =>{
      getSearchData();
      //console.log(SearchData);
      },[keyWord]
    )
    
    if(isLoading){
      return <p
      style = {{
        fontSize : '30px',
        fontFamily : "Fantasy"

      }}
      >We are trying to get your results now...&#128147;&#128527;</p>
    }
    return(
        <Layout>
    <Header
      style={{
        position: 'sticky',
        top: 0,
        zIndex: 1,
        width: '100%',
        height: '90px',
        backgroundColor: '#000000'
      }}
    >
        <Menu
            style={{backgroundColor: '#000000'}}
            theme="light"
            mode="horizontal"
            
            items={[
              {
                key: '1',
                id : 'Icon',
                icon:<Image 
                        style={{
                          position: 'absolute', 
                          top:'15px',
                        }}
                    height = {63}
                    width ={180}
                    src = {logo}
                    onClick={backToHome}
                />
              },
              {
                key: '2',
                id : "SearchBar",
                style: {position:'absolute',left:'40%'},
                label :<Search
                       
                        size = "large"
                        placeholder="input search text"
                        onSearch={onSearch}
                        allowClear
                        suffix={suffix}
                />
              }
            ]}
          />
     
    </Header>

    <Content
      className="site-layout"
      style={{
        padding: '0 50px',
        marginTop: 64,
      }}
    >
        <List
            itemLayout="vertical"
            size="large"
            pagination={{
            onChange: (page) => {
                console.log(page);
            },
            pageSize: 10,
            }}
            dataSource={SearchData}
            renderItem={(item) => (

            // **Fllowing code would be ready if the json format is complete
            <List.Item                    
                // key={item.id}
                key={item.url}
            >
                <i id = 'url'
                  style = {{
                    fontSize: '14px'
                   
                  }}
                >{item.url}</i>
                <List.Item.Meta
                
                title={<a 
                        href={item.url}
                        style={{fontFamily : "Fantasy",
                                fontSize: '30px',
                                color: '#0074B7'
                            }}
                        >
                        {item.title?item.title:item.url}
                        </a>}
                />
                <div
                    style={{fontFamily : "serif",
                    fontSize: '25px',
                    color: '#707071'
                }}
                >
                    {/* {item.short_description?"Not available for descriptions":item.short_description} */}
                    {item.body?item.body:"Not available for descriptions"}
                </div>
                
            </List.Item>

            // <List.Item
            //     key = {item.url}
            // >
            //          <List.Item.Meta
            //     title={<a 
            //             href={item.url}
            //             style={{fontFamily : "Fantasy",
            //                     fontSize: '28px',
            //                     color: '#707071'
            //                 }}
            //             >
            //             {item.tiltle? item.title:item.url}
            //             </a>}
            //     />
            //     <div
            //         style={{fontFamily : "serif",
            //         fontSize: '17px',
            //         color: '#707071'
            //     }}
            //     >
            //         {item.rank_score}
            //     </div>
            // </List.Item>
            )}
        />

    </Content>

    <Footer
      style={{
        textAlign: 'center',
      }}
    >
      Yiqi & Fred & Xinyue ©2022 University of Pennsylvania
    </Footer>

  </Layout>
    )
}
